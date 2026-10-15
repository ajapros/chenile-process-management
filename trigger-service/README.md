# Trigger service

`trigger-service` implements the contracts in [trigger-api](../trigger-api/README.md).
It converts an external activation into a synchronously dispatched, in-JVM
Chenile event. It uses the existing `EventProcessor`; it does not introduce another event bus.

It owns trigger claim persistence and execution history, with no dependency on Quartz,
`crontab-trigger`, or the process manager. Cron support is provided separately by
[crontab-trigger](../crontab-trigger/README.md), which depends only on the trigger API.
Applications select these modules explicitly. `process-service` marks both implementation
dependencies optional; the admin server selects both.

Scan `org.chenile.configuration.trigger` or import `ChenileTriggerConfiguration` to wire
the implementation. Applications own entity scanning of `org.chenile.trigger.model` and
repository scanning of `org.chenile.trigger.store`; configuration classes do not declare scans.

## Fully formed trigger events

Every adapter supplies a `TriggerInput`: a trigger ID, an event ID, event payload, event
headers, and a trigger time. `TriggerService` does not map, enrich, or route that input. It
records the received trigger, uses the Chenile event definition to convert the payload to the
declared Java event type, and synchronously calls the existing `EventProcessor`.
Dispatch supplies the trigger ID through `x-chenile-trigger-id` in a copy of the event headers.
The configured payload and original headers are preserved.

The trigger ID and event ID form the idempotency key. The framework generates a trigger ID when
an adapter does not provide one. The initial adapter is the durable Quartz crontab; HTTP is not a
trigger adapter.

## Durable crontabs

The separate `crontab-trigger` module stores Quartz schedules in `chenile_crontab`. The entity is managed through
`CrontabService`; saving, disabling, or deleting a row registers, removes, or reschedules its
Quartz job. The runtime schema is generated through JPA; an equivalent schema must include:

- `id`, unique `name`, `enabled`, `cron_expression`, and `timezone`;
- `event_name`, optional `headers_json`, and optional `event_payload_json`;
- standard Chenile entity audit fields (`id`, created time, modified time, tenant, and version).

`event_payload_json` is always the complete payload for `event_name`. A `ProcessCreate` crontab,
for example, stores its `processDefName` and arguments inside that JSON payload. Every firing
supplies a stable trigger ID of the form `crontab:{crontabId}:{quartzFireInstanceId}`, so duplicate
dispatches are suppressed while each scheduled run remains distinct.

## Process starts and workers

`process-service` exposes `ProcessCreate` through its existing process controller and as an
in-JVM event subscription. It creates the root `Process` and then follows the existing
process-manager path. Existing `WorkerStarter` implementations decide whether splitters,
executors, and aggregators run in this JVM, through a queue, or through JDBC work items.

The same `ProcessCreate` operation is also available through the existing process service at
`POST /process`; its request body is a `ProcessDto` payload for the `ProcessCreate` event.
When that payload omits `triggerId`, process creation uses `x-chenile-trigger-id` from the
adapter, correlating each scheduled run with its trigger claim.
Tenant identity is supplied through the standard `x-chenile-tenant-id` header and is persisted
as the inherited `BaseJpaEntity.tenant` value.

## Unified trigger storage

`TriggerLog` maps to `trigger_log`. One retained row owns both the unique
`(trigger_id, event_name)` claim and dispatch metadata: tenant, source, crontab ID,
payload, start/finish time and error. The lifecycle is `RECEIVED` → `RUNNING` →
`COMPLETED` / `FAILED`; each write uses an independent transaction. Duplicate claims
are suppressed in every state, including failures and interrupted dispatches.
No automatic retry is introduced. `/executions` reads these same records.

Replace application references to `TriggerExecution` / `TriggerExecutionRepository` with
`TriggerLog` / `TriggerLogRepository`. Custom `TriggerLogStore` implementations now provide
`markRunning(id)` and accept the dispatch exception in `markFailed(id, failure)`.

`TriggerObserver` is an optional extension for metrics or other observations. Its
default is `NONE`; durable lifecycle writes occur regardless of the selected observer.
Observer failures are logged and do not turn a successful dispatch into a failure.
Process completion and delivery receipts remain separate from trigger idempotency.

Apply `chenile-trigger-schema.sql` for new databases. Existing installations must stop
all trigger producers, back up the two old tables, and run `chenile-trigger-unification.sql`
as a single PostgreSQL transaction before starting the upgraded application:

```sh
psql --single-transaction --file=chenile-trigger-unification.sql "$DATABASE_URL"
```

The optional `db/changelog/chenile-trigger.changelog.xml` applies the same upgrade in
Liquibase's transaction. It is included by the admin master changelog. Existing claim
IDs and terminal statuses take precedence; `RECEIVED` with a running execution becomes
`RUNNING`. Matching execution metadata is copied by trigger/event identity. Claims
without history retain nullable metadata. Execution-only rows become claims. Duplicate
execution keys or conflicting IDs abort the upgrade for reconciliation. The old execution
table is dropped only after merging. The migration can be reapplied.

Retain claim identity and status for the entire duplicate-delivery horizon. Do not delete
rows merely to prune history: clear `payload` and `error` instead. Deleting a row permits
that trigger/event to dispatch again. Reusing a key with a different tenant or payload
still returns the existing claim; the unique key remains global, and adapters must
namespace tenant-specific trigger IDs as before.
