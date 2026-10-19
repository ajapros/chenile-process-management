# Crontab service

`crontab-api` owns the `Crontab` model and the `CrontabService`/`CrontabReader` contracts.
`crontab-service` implements them with JPA persistence, JSON schedule loading, validation,
Quartz scheduling, and a Chenile HTTP controller. It depends on `trigger-api`, never on
`trigger-service` or `process-service`. The caller's event payload is passed unchanged.

## Wiring and migration

Replace the former `crontab-trigger` dependency with `crontab-service`; use `crontab-api`
when only models/contracts are needed. Existing Java packages, table names, JSON schedule
format, Quartz job keys, and `CrontabTriggerConfiguration` imports are retained. The
`crontabService` bean name remains an alias for the blueprint-style `_crontabService_` bean.

The host supplies `TriggerService`, a Quartz `Scheduler`, and persistence discovery. Scan
`org.chenile.configuration.crontab` or import `CrontabTriggerConfiguration`. The host application
scans `org.chenile.trigger.cron` for entities and repositories. Library configuration never
uses entity or repository scanning annotations.

## HTTP contract

The controller follows the Chenile service blueprint: `@RestController`, `@ChenileController`,
`ControllerSupport`, and delegation exclusively through `process(...)`. Service logic owns
validation, persistence, tenant checks, and post-commit scheduling.

| Method | URL | Service operation |
| --- | --- | --- |
| POST | `/crontab` | `save(Crontab)`; create or update using the body's optional ID |
| GET | `/crontab/{id}` | `retrieve(id)` |
| GET | `/crontab` | `findAll()` for the current tenant |
| DELETE | `/crontab/{id}` | `delete(id)` |

Pass `x-chenile-tenant-id` on requests and `x-chenile-auth-user` for audit attribution.
Responses use the standard `GenericResponse` envelope and HTTP 200 on success. Invalid
schedules or conflicting body tenants return Chenile 400 errors; missing or foreign-tenant
IDs return 404. Saving persists and flushes the entity, then changes Quartz only after commit.
Disabling removes its scheduled job; deleting removes both the row and the committed schedule.
The host applies its normal authentication/authorization policy to these exposed routes.

```json
{
  "name": "nightly",
  "cronExpression": "0 0 0 1 1 ? 2099",
  "timezone": "UTC",
  "eventName": "CustomerEvent",
  "headersJson": "{\"x-request-id\":\"nightly-1\"}",
  "eventPayloadJson": "{\"customerId\":7}"
}
```

## Trigger delivery

Each firing uses `crontab:{crontabId}:{quartzFireInstanceId}` as its submission idempotency key
and sets `TriggerInput.source` to `CRON`. The chosen trigger service assigns the acknowledged
trigger ID. Cron identity, Quartz timing, and generated/configured x-headers go into
`triggerContext`. Event payload strings remain untouched until subscriber transformation.
See [trigger-service](../trigger-service/README.md) for durable acceptance and execution semantics.

## Verification

`mvn test -pl crontab-service -am` runs scheduler/JSON/transaction tests and the Cucumber REST
feature. BDD uses the real controller, Chenile highway, service implementation, and H2 database;
only Quartz and event submission are mocked. It covers CRUD, audit/tenant metadata, deferred
scheduling, invalid cron/timezone/header input, and isolation from other tenants.
