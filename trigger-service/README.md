# Durable trigger service

`trigger-service` accepts an event invocation into `chenile-outbox`. It depends on neither
Quartz nor the process manager. `crontab-trigger` continues to depend only on `trigger-api`.
There is no trigger table, trigger JPA entity, trigger repository, or trigger observer.

## Submit and acknowledge

```json
{
  "idempotencyKey": "optional-client-retry-key",
  "eventId": "ProcessCreate",
  "headers": {"x-chenile-tenant-id": "alpha"},
  "triggerContext": {"adapter": "HTTP"},
  "payload": {"processDefName": "feed", "args": {"file": "input.csv"}}
}
```

`POST /trigger` returns HTTP 202, a Location of `/trigger/<id>`, and the usual Chenile
response envelope whose payload is only `{"triggerId":"<outbox-id>"}`. Acceptance commits
before the ID is returned, including when the caller has an outer transaction that rolls back.
No subscriber is invoked by submission. Invocation output is logged during later dispatch;
it is not included in the acknowledgement or status response.

The optional idempotency key is scoped by tenant and event. Concurrent submissions with the
same key return the original acknowledgement. Without a key, every submission is new.
The caller cannot supply the accepted trigger ID: it is the outbox command ID. Dispatch
sets `x-chenile-trigger-id` to that ID, and any resulting process stores it as its trigger ID.
An authoritative caller tenant cannot be overridden by a conflicting payload header.

## Query and execute

`GET /trigger/<id>` returns this tenant's status and original input metadata, or 404.
States are PENDING, RUNNING, DONE and DEAD. Retries retain the same ID, increment the attempt
count, and use the shared outbox backoff, lease fencing and operator replay facilities.
A missing subscriber is a dispatch failure, not a successful no-op.

The dispatcher restores persisted x-headers and tenant context. It passes the original event
payload to EventProcessor; Chenile selects and converts the subscriber's input type. Cron
payload strings remain strings until that framework transformation. Local subscriber changes
and DONE acknowledgement commit together. Failed attempts roll back local effects before retry.
External systems still need their own idempotency: arbitrary external effects cannot be made
exactly once by a local database transaction.

## Configuration and retention

Include `trigger-service` and its transitive `chenile-outbox` infrastructure, a datasource and
transaction manager, and normal Chenile event wiring. Import ChenileTriggerConfiguration or
scan its configuration package. Framework configuration never declares persistence scans.

Use the existing `chenile.process.outbox.*` dispatcher settings. The shared SQL table/resource
names and Java package names are retained for compatibility; process_id is optional because
triggers precede processes. Status/history is a view of DISPATCH_TRIGGER commands.

Retain those command identities and envelopes: deleting a trigger row removes both its status
and submission deduplication. Large business payloads can be pruned according to a separate
retention policy only if the remaining envelope still supports status decoding. There is no
automatic trigger command deletion. Retain DEAD commands for diagnosis or explicit replay.
