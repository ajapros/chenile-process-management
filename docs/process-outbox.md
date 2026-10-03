# Durable process outbox

Apply `process-outbox/src/main/resources/chenile-process-outbox-schema.sql` with your migration tool, then enable:

```properties
chenile.process.outbox.enabled=true
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
`process-outbox/src/main/resources/chenile-process-outbox-json-migration.sql` with your PostgreSQL
migration tool (or psql). It wraps existing parent-signal bodies in JSON and drops `target_id`,
`event_name`, and `worker_type`, preserving row IDs, delivery state, idempotency keys, and receipts.
The script is transactional and can be reapplied; malformed/null legacy payloads must be corrected
before upgrading. Do not run old and new producers/dispatchers concurrently during migration.
PostgreSQL is the reference
database. Process JPA, outbox JDBC, and the JDBC starter must share a DataSource and transaction manager.
Provision at least two connections per concurrent dispatch thread plus request traffic, for independent
handler and after-commit publication transactions. Use consistent UTC database/JDBC timezones for leases.

Every enabled replica polls concurrently using row locks. Set `run-dispatcher=false` only when another
replica drains work or tests call `OutboxDispatcher.drainAll(100)` **after commit**. Never drain inside the
transaction that created work. Durable create returns before its workers, children, and successors finish.

## Extending commands

`OutboxCommandLifecycle<C>` is the single lifecycle contract: `enqueue(context)`,
`dispatch(entry)`, and `onDead(entry)`, plus a stable `type()` discriminator.
`CreateSubProcessCommand`, `SignalParentCommand`, `EmitCompletedCommand`, and
`StartWorkerCommand` each own their JSON schema, idempotency identity, and behavior.
`ProcessEntryAction` always delegates to `ProcessEffects`, which passes a `ProcessTransition` to the registry; the dispatcher
resolves the entry's type and calls the same command's dispatch/dead method. There is no central enum
or switch to extend. Duplicate type registration fails at startup; unregistered persisted types
retry and eventually become DEAD rather than being silently acknowledged.

Add a Spring bean implementing `OutboxCommandLifecycle<ProcessTransition>` (or subclass
`AbstractProcessOutboxCommand`) to register another process command automatically. Its enqueue
method decides whether the transition needs that effect. Use the abstract base's `store(...)` helper
or `ProcessCommandSupport.enqueue(...)` to choose durable persistence or immediate execution centrally;
use `read(...)` for the typed body and `receive(...)` for mode-aware receipts. Do not bypass this
support by writing directly to the repository if the command must also work in inline mode.
The abstract base provides delivery and tenant restoration; each implementation provides its dispatch
and optional dead behavior.
No new database columns are needed.

The four commands and registry are wired in both modes. With the outbox disabled (the default),
enqueue immediately calls the same command's dispatch method, preserving the live typed process/worker
objects so synchronous cascades and their returned state remain intact. No outbox repository, tables,
receipts, polling, or transaction synchronization is required. Completion is published synchronously
through EventProcessor exactly once, which invokes the framework subscriber and application listeners.
Without a configured WorkerStarter, inline worker dispatch remains a no-op as before.

With the outbox enabled, command bodies are serialized to JSON, receipt checks are transactional,
and completion chaining runs in the handler transaction with best-effort fan-out after commit.
Automatic retries/dead callbacks are a durable-transport policy; commands' onDead methods can also
be invoked explicitly in inline mode, but inline dispatch failures propagate to the caller.

The table stores shared delivery metadata (process/tenant identity, type, deduplication key, status,
retry/lease timestamps) and a `payload` text column containing command-owned JSON. For example,
SIGNAL_PARENT contains `parentId`, `childId`, `eventName`, and its typed `payload`; START_WORKER
contains the complete WorkerDto, including workerType. New commands own their payload evolution.
Dispatch and receipts remain atomic with acknowledgement. Dead callbacks are best-effort after
DEAD commits; they are not a durable compensation queue and are not automatically retried.

## Guarantees

State and consequences commit atomically. Framework child creation, parent counting, and chaining use
transactional receipts separate from TriggerLog. Repeated completion ingress creates each matching successor
once when enabled. Direct ProcessDto creation remains non-deduplicated. Disabling durability restores
synchronous behavior and completion ingress can create repeated successors.

Application ProcessCompleted listeners are best-effort after commit. External starters must deduplicate
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

## Verification

Normal `mvn test` runs H2 and legacy synchronous regressions. PostgreSQL tests are opt-in:

```sh
mvn test -Dchenile.outbox.test.jdbc-url=jdbc:postgresql://localhost:5432/test_database \
  -Dchenile.outbox.test.username=test_user
```

Use a test database/user with CREATE SCHEMA permission. Each test creates/removes its own UUID-named schema,
never existing application tables. Set `chenile.outbox.test.password` if needed without exposing production
credentials in shell history. See [ADR 0001](adr/0001-durable-outbox-for-process-entry-action.md).
