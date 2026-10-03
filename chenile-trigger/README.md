# Chenile Trigger

`chenile-trigger` converts an external activation into a synchronously dispatched, in-JVM
Chenile event. It uses the existing `EventProcessor`; it does not introduce another event bus.

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

Quartz schedules are stored in `chenile_crontab`. The entity is managed through
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

## Trigger idempotency storage

`TriggerLog` in this module maps to `trigger_log`. It contains only `id`, `trigger_id`,
`event_name`, `trigger_time`, and `status` (`RECEIVED`, `COMPLETED`, or `FAILED`). The database
uniquely constrains `(trigger_id, event_name)`; no concatenated key is needed. Claims and status
updates commit independently of event dispatch. Existing claims suppress repeated dispatch,
including failed or interrupted claims; there is no automatic retry.

This store is used exclusively by `TriggerService`. Process creation, transitions, workers,
and completion events do not write records here. A trigger may target `ProcessCreate`, but
its record identifies the trigger dispatch, not the process creation operation. Direct
process creation does not consult this table.

Existing installations must migrate trigger claims to `trigger_log` before switching dispatch:
copy only former trigger rows, preserve their IDs and statuses, and use `logged_at` as
`trigger_time`. Process lifecycle rows are excluded. The obsolete generic logger module has
been removed; this change does not drop existing database tables.
