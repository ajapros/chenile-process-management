# ADR 0001 — Durable outbox for ProcessEntryAction and framework process chaining

Status: Accepted — implemented, opt-in
Date: 2026-10-01
Related: [process-management guide](../process-management.md), [operations](../process-outbox.md)

## Context and decision

The default entry action saves a process then performs child creation, parent notification, completion
publication, and worker dispatch inline. A crash between save and effect can strand the tree.
`TriggerLog` handles trigger dispatch idempotency only, not process effects.

Use PostgreSQL as the reference database, enabled explicitly with `chenile.process.outbox.enabled=true`.
Leave legacy synchronous behavior unchanged when disabled. The first version guarantees
**framework-managed process chaining**, not durable delivery to arbitrary subscribers or external services.
Authoritative state remains in the application database; Temporal-style deterministic replay is not adopted.

`process-outbox` contains independent infrastructure. `process-service` depends on it and provides typed
handlers. Moving the prototype from `jdbc-process-starter` avoids a circular dependency.

The command lifecycle is unified in `OutboxCommandLifecycle<C>`: enqueue, dispatch, and onDead.
Four separate command implementations own their JSON schemas, receipts, and dead behavior.
The entry action uses these commands in both modes. Shared delivery support serializes/enqueues in
durable mode and directly dispatches live typed bodies in inline mode. Inline receipts are bypassed,
and completion publication is synchronous rather than registering an after-commit callback.
Only the repository/poller beans are conditional; command beans and the registry are always wired.
`OutboxCommandRegistry` selects implementations using stable string type names; extensions register
a Spring bean without changing an enum, a central switch, or the table. Shared delivery metadata
remains in columns; command-specific addressing/events/worker information lives in the JSON payload.
The old handler/dead-handler interfaces and command-type enum are removed. An explicit PostgreSQL
migration converts legacy SIGNAL_PARENT payloads and drops the three command-specific columns while
preserving command IDs, delivery state, idempotency keys, and receipts.

| Command | Framework effect |
|---|---|
| CREATE_SUBPROCESS | Create child using its ID allocated before enqueue |
| SIGNAL_PARENT | Count each child once and advance its locked parent |
| EMIT_COMPLETED | Create matching successors once per predecessor/successor process type |
| START_WORKER | Hand a serialized WorkerDto to the configured starter |

## Transactions and recovery

Durable manager creation, mutation, and completion ingress use REQUIRED transactions. Existing processes
are pessimistically locked before applying events. JPA save, JDBC enqueue, and consumer receipts must
use the **same DataSource and transaction manager**. Enqueue/receipt insertion require an ambient transaction.

Claim, failure, and operator replay use REQUIRES_NEW. Handling locks the claimed row and validates its
unique token before executing. Framework business changes, receipts, new commands, and DONE acknowledgement
commit together in one independent transaction. A failed handler rolls all of these back before recording
retry/dead state separately. PostgreSQL inserts use `ON CONFLICT DO NOTHING`, including JDBC worker handoff;
catching duplicate-key exceptions is insufficient because that database transaction is already aborted.

Claims use `FOR UPDATE SKIP LOCKED`, a lease, and a new token per attempt. Stale claimants cannot execute,
acknowledge, or fail reclaimed work. Expired final attempts become DEAD. Recovery skips rows locked by active
handlers, including final-attempt cleanup. A lease recovers a dead process; it does not interrupt a live transaction.

Unique outbox keys suppress duplicate enqueue. Separate `chenile_process_receipt` rows suppress duplicate
effects, committed alongside the effect: CREATE uses child identity; PARENT uses parent/child identity
independent of success/error; CHAIN uses predecessor ID and successor process type; WORKER uses dispatch identity.
Child creation also checks existence. Parent receipts prevent blind counter increments on replay.

JDBC work insertion on the same database joins the handler transaction. External starters must deduplicate
`WorkerDto.dispatchId` at their receiver: a database transaction cannot commit an arbitrary network effect
atomically. In-VM workers re-entering the manager join the handler transaction and must avoid irreversible
external effects if rollback safety is required.

## Completion scope

EMIT_COMPLETED calls `ProcessManager.processCompleted` directly inside the handler transaction. Definitions
matching `predecessorProcessType` choose input/output/both as before, including failed predecessor completion.
Receipts commit with successor creation and its work. Direct completion ingress uses the same receipts in durable mode.

After commit, the existing EventProcessor also publishes to application subscriptions in a fresh transaction
(after-commit callbacks still have old resources bound). A second framework subscriber invocation safely finds
existing CHAIN receipts. Application fan-out is best-effort: failures are logged and a crash before fan-out
can lose delivery. Broker-backed subscribers with independent acknowledgements are a future extension.

## Scheduling and operations

The opt-in poller drains up to 100 due commands per tick. Tests can explicitly call `drainAll(n)` after commit;
it is bounded and stops when nothing is immediately due, not when delayed/concurrent work finishes.
Durable create does not promise a completed cascade on return. Legacy tests run with durability disabled.

Retries use exponential backoff and a maximum attempt count. DEAD rows generate error logs and are exposed
through `deadCount()`. Operators correct the cause then selectively `replayDead(id)`. Receipts survive replay
and must remain for the entire redelivery horizon.

A dead **START_WORKER** is auto-compensated, because it is the one dead command that would otherwise park
its process forever in a work-pending state waiting for a worker that will never run. When such a command
exhausts its retries, the owning claimant invokes `StartWorkerCommand.onDead`, which
drives the stranded process to its error terminal with the state-appropriate error event — `SPLITTER` →
`splitDoneWithErrors`, `EXECUTOR` → `doneWithErrors`, `AGGREGATOR` → `aggregationDoneWithErrors` — which then
cascades `SIGNAL_PARENT` and `EMIT_COMPLETED` durably so the subtree unwinds as errors. The compensation runs
in its own REQUIRES_NEW transaction, is fenced to the owning claimant (a stale claimant whose `markFailure`
matched nothing does not compensate), and is best-effort (failures are logged, never rethrown into the poller).
The other dead command types (CREATE_SUBPROCESS, SIGNAL_PARENT, EMIT_COMPLETED) are **not** auto-compensated —
their correct recovery is application-specific — and remain for operator `replayDead(id)`.

See [operations](../process-outbox.md) for migration, monitoring, and retention.

## Verification and consequences

Tests cover process/outbox rollback, deferred dispatch, child creation, replay-safe parent counting and
successor creation. Opt-in PostgreSQL tests cover competing connections, stale-token fencing, final-attempt
crash recovery, non-aborting duplicate inserts, and business/receipt rollback before acknowledgement.
H2 remains a fast regression fixture, not proof of PostgreSQL locking semantics.

Costs include extra rows, connection capacity, explicit schema migration, monitoring, and asynchronous
cascades. Shared transaction resources and external consumer deduplication are deployment requirements.
