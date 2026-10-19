# Durable process outbox

Apply `chenile-outbox/src/main/resources/chenile-process-outbox-schema.sql` with your migration tool. Durable delivery is mandatory; configure polling with:

```properties
chenile.process.outbox.run-dispatcher=true
chenile.process.outbox.poll-interval-millis=1000
chenile.process.outbox.worker-id=process-replica-1
chenile.process.outbox.lock-seconds=300
chenile.process.outbox.max-attempts=5
```

For local development, Spring SQL initialization can load the packaged resource:

```properties
spring.sql.init.mode=always
spring.sql.init.schema-locations=classpath:chenile-process-outbox-schema.sql
```

This is an initial schema, not an upgrade migration. Existing prototype tables need the new `claim_token`
and `tenant_id` columns and the receipt table added before enabling this version. For the earlier
column-based command schema, stop producers/dispatchers, back up the table, and apply
`chenile-outbox/src/main/resources/chenile-process-outbox-json-migration.sql` with your PostgreSQL
migration tool (or psql). It wraps existing parent-signal bodies in JSON and drops `target_id`,
`event_name`, and `worker_type`, preserving row IDs, delivery state, idempotency keys, and receipts.
The script is transactional and can be reapplied; malformed/null legacy payloads must be corrected
before upgrading. Do not run old and new producers/dispatchers concurrently during migration.
PostgreSQL is the reference
database. Process JPA, outbox JDBC, and the JDBC starter must share a DataSource and transaction manager.
Provision at least two connections per concurrent dispatch thread plus request traffic, for independent
handler transactions and request traffic. Use consistent UTC database/JDBC timezones for leases.

`ProcessOutboxConfiguration` is supplied by `chenile-outbox` in the existing
`org.chenile.orchestrator.process.configuration` package. The application's normal
configuration scan discovers it; `ProcessConfiguration` does not import it.
It accepts the generic command registry without depending on `process-service`.
Outbox infrastructure is always wired when its configuration is included.
There is no enablement setting; only background polling can be disabled.

`OutboxDispatcher.poll()` owns each background cycle: it drains up to 100 commands
and reports DEAD commands. The configured fixed-delay task is registered only when
`run-dispatcher` is true (the default). Disabling it leaves the dispatcher available
for manual `processOne()` and `drainAll(...)` calls without registering a polling task.
`process-service` still depends on `chenile-outbox` for the shared command contracts;
this configuration move does not make that artifact dependency optional.

Every enabled replica polls concurrently using row locks. Set `run-dispatcher=false` only when another
replica drains work or tests call `OutboxDispatcher.drainAll(100)` **after commit**. Never drain inside the
transaction that created work. Durable create returns before its workers, children, and successors finish.

## Extending commands

`OutboxCommandLifecycle<C>` is the single lifecycle contract: `enqueue(context)`,
`dispatch(entry)`, and `onDead(entry)`, plus a stable `type()` discriminator.
`CreateSubProcessCommand`, `SignalParentCommand`, `EmitCompletedCommand`, and
`StartWorkerCommand` each own their JSON schema, idempotency identity, and behavior.
`ProcessEntryAction` passes a `ProcessTransition` directly to `OutboxCommandRegistry`; the dispatcher
resolves the entry's type and calls the same command's dispatch/dead method. There is no central enum
or switch to extend. Duplicate type registration fails at startup; unregistered persisted types
retry and eventually become DEAD rather than being silently acknowledged.

Add a Spring bean implementing `OutboxCommandLifecycle<ProcessTransition>` (or subclass
`AbstractProcessOutboxCommand`) to register another process command automatically. Its enqueue
method decides whether the transition needs that effect. Use the abstract base's `store(...)` helper
to record durable JSON commands. Use `read(...)` for typed bodies and
`checkIdempotency(...)` for atomic receipt claims (true permits execution).
The abstract base provides tenant restoration and independent transaction support.
Custom subclasses pass their shared dependencies to its constructor.

Every manager transition executes transactionally: its process save and recorded
commands commit together. Commands execute only after that commit, in independent
handler transactions. A child/parent/successor transition performed by a handler
records its own consequences; those consequences become deliverable after the
handler transaction commits. Enqueue never invokes a command directly.

`EMIT_COMPLETED` publishes once through EventProcessor. Its registered framework
subscriber calls `processCompleted` through Chenile and creates matching successors.
Application subscribers execute in the same command transaction. Subscriber
failures, or a missing completion subscriber, fail the command and trigger retry;
there is no direct manager call and no second after-commit publication.

A missing WorkerStarter fails dispatch and retries. Even in-VM workers are launched
from committed commands. Process creation returns the initial committed state,
not the final state of a synchronous cascade. Tests explicitly drain after commit.

The table stores shared delivery metadata (process/tenant identity, type, deduplication key, status,
retry/lease timestamps) and a `payload` text column containing command-owned JSON. For example,
SIGNAL_PARENT contains `parentId`, `childId`, `eventName`, and its typed `payload`; START_WORKER
contains the complete WorkerDto, including workerType. New commands own their payload evolution.
Dispatch and receipts remain atomic with acknowledgement. Dead callbacks are best-effort after
DEAD commits; they are not a durable compensation queue and are not automatically retried.

## Guarantees

State and consequences commit atomically. Framework child creation, parent counting, and chaining use
transactional receipts. Repeated completion ingress creates each matching successor
once. Direct ProcessDto creation remains non-deduplicated. Non-durable execution is no longer supported.

Application ProcessCompleted listeners run in the command transaction and failures trigger retries. External starters must deduplicate
`WorkerDto.dispatchId`. Prefer the JDBC starter for transactional handoff; worker execution/retry lifecycle
remains separate from outbox command delivery.

## Monitoring, replay, and retention

`backlogCount()` counts immediately due work including expired leases; `deadCount()` counts exhausted work.
Monitor both, oldest outstanding age, and errors:

```sql
select status, count(*), min(created_at) as oldest_created_at
from chenile_process_outbox where status <> 'DONE' group by status;

select id, process_id, command_type, attempt, error_message
from chenile_process_outbox where status = 'DEAD' order by updated_at;
```

Fix the cause, then call `ProcessOutboxRepository.replayDead(id)` for selected work. This resets attempts
and visibility, preserving identity and receipts. Do not clear receipts: they mean an effect already committed.
Stale workers remain fenced. No unsecured replay endpoint is provided.

A dead **START_WORKER** is the exception to manual-only recovery: it is auto-compensated. Because a worker
that never runs would otherwise park its process forever in SPLIT_PENDING / EXECUTING / AGGREGATION_PENDING,
the owning claimant drives that process to its error terminal (`splitDoneWithErrors` / `doneWithErrors` /
`aggregationDoneWithErrors` by worker type), which cascades parent notification and completion so the subtree
unwinds as errors. The process therefore reaches `PROCESSED_WITH_ERRORS` rather than hanging; the dead row is
still logged and visible in `deadCount()` for diagnosis. The other dead command types are not auto-compensated
and remain for `replayDead(id)`.

Archive/delete DONE rows after your operational horizon with a scoped, batched procedure. Retain receipts
at least as long as any corresponding child/predecessor/completion can be redelivered; keep them indefinitely
by default. Receipt cleanup needs an application-defined retention policy and must not overlap possible replay.

## Why the repository is JDBC, not JPA

`ProcessOutboxRepository` is deliberately hand-written JDBC (`JdbcTemplate` + native SQL) rather than a JPA
entity with an `EntityManager`/Spring Data repository. This is a design choice, not unfinished work: the table
is a concurrent work queue, and its correctness depends on database primitives that JPA cannot express
portably — or, in one case, cannot express safely.

- **Set-based compare-and-set is the concurrency model.** `claimNext` uses
  `select … limit 1 for update skip locked`; `acknowledge`, `markFailure`, and the lease hand-off in
  `handleAndComplete` are each a single `update … where id=? and status='RUNNING' and claim_token=?` whose
  **affected-row count (== 1)** is the fence. One atomic statement is race-safe and auditable in one place.
  Re-expressing it in JPA means load → check → mutate → flush, which either reopens a time-of-check/
  time-of-use window or needs pessimistic locks and extra round-trips, and the managed-entity lifecycle
  (dirty checking, first-level cache, flush ordering) adds risk for no benefit on a hot poller.
- **`ON CONFLICT DO NOTHING` is required for safety, not convenience.** `enqueue` and `checkIdempotency` run
  **inside the caller's business transaction** — the same transaction that saves the `Process`. In PostgreSQL
  a unique-constraint violation marks the whole transaction for rollback, so the idiomatic JPA pattern
  (`persist` then catch `DataIntegrityViolationException`) would poison the `Process` save it shares a
  transaction with. `insert … on conflict do nothing` avoids aborting the transaction and returns a rowcount
  the caller reads to detect the duplicate. Routing these through JPA would force a native `insert … on
  conflict` through the `EntityManager` anyway — JDBC with extra ceremony.
- **JPA buys no portability here.** `SKIP LOCKED` and `ON CONFLICT` pin the implementation to PostgreSQL
  regardless of the access layer (Hibernate 6 can emit `SKIP_LOCKED` only via a non-standard lock hint, and
  has no `ON CONFLICT` equivalent). The reference database is PostgreSQL either way, so the usual headline
  reason to adopt JPA does not apply.
- **The row is mapped to a plain DTO on purpose.** `map(...)` produces a detached `OutboxCommand`, never a
  managed entity, so nothing is tracked or accidentally flushed. Conditional/bulk SQL
  (`coalesce(started_at, ?)`, `case when …='DEAD'`, the lease-expiry `DEAD` sweep, `tenant_id is not distinct
  from ?`) is clean in SQL and clumsy in JPQL — and JPQL bulk updates bypass the persistence context anyway.

The shared-datasource invariant holds either way: because `JdbcTemplate` and the process JPA share one
`DataSource` and transaction manager (enforced at runtime by `requireTransaction()`), the native enqueue and
receipt inserts already commit in the same transaction as the `Process` save.

If a future change wants more ORM consistency, the defensible shape is a **hybrid, not a conversion**: keep
the hot path (`enqueue`, `claimNext`, `acknowledge`, `markFailure`, `checkIdempotency`) as native SQL, and
optionally model the table as a read-only `@Entity` only for the cold read/admin surface (`findById`,
`findByIdempotencyKey`, the paginated `findCommands`/`findByIds`). The queue mechanics should not move into
the persistence context.

## Verification

Normal `mvn test` runs H2 durable delivery and recursive worker regressions. PostgreSQL tests are opt-in:

```sh
mvn test -Dchenile.outbox.test.jdbc-url=jdbc:postgresql://localhost:5432/test_database \
  -Dchenile.outbox.test.username=test_user
```

Use a test database/user with CREATE SCHEMA permission. Each test creates/removes its own UUID-named schema,
never existing application tables. Set `chenile.outbox.test.password` if needed without exposing production
credentials in shell history. See [ADR 0001](adr/0001-durable-outbox-for-process-entry-action.md).

## Trigger acceptance uses the same outbox

DISPATCH_TRIGGER stores an event envelope without a process owner. Submission commits before returning
only its command ID; that is also its trigger ID and the x-chenile-trigger-id used by resulting processes.
An optional tenant/event-scoped submission idempotency key returns the original ID on retries.
GET /trigger/{id} reads status in the current tenant. There is no trigger_log table.

OutboxCommandRegistry registers dispatch handlers of any context type. OutboxCommandProducer<C>
selects only the handlers belonging to that producer's context. It calls shouldEnqueue(context), which
performs no writes, then enqueue(context). Process transitions never fan out into trigger submission.

Retain DISPATCH_TRIGGER identities and decodable envelopes for status and duplicate suppression.
The current table records the latest attempt/error, not an immutable full attempt history. The existing
SQL table/resource names and Java packages remain compatible; the artifact is now chenile-outbox.

## Execution context and transaction completion

The shared outbox execution boundary installs the command tenant and optional lifecycle
`executionHeaders(command)` in an isolated Chenile context after validating the lease. Handler
headers cannot override the row tenant. It reinstalls this context when dispatch returns, since
Chenile event processing may clear it, and retains it through transaction commit or rollback.
This keeps tenant and audit headers available for deferred JPA flushes and transaction callbacks.
The caller's complete context is restored only after the transaction finishes, on both success
and failure. Ordinary commands default to no invocation headers. Trigger commands supply persisted
headers and their authoritative trigger ID; they contain no transaction synchronization or context
cleanup code. `chenile-outbox` depends on the core Chenile context runtime, not on trigger or process APIs.
