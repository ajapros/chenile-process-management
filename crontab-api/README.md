# Crontab API

`crontab-api` owns `Crontab`, the JSON document model `CrontabEntry`, `CrontabService`, and `CrontabReader` in the existing
`org.chenile.trigger.cron` package. It contains no Quartz scheduler, repository implementation,
HTTP controller, or runtime configuration. It depends on `jpa-utils` for the shared JPA entity
base, following the Chenile service blueprint.

`CrontabService` exposes `save(Crontab)`, `retrieve(String)`, `findAll()`, and `delete(String)`.
`CrontabReader` is the schedule source contract implemented by database and JSON readers.
Applications consume this artifact for models/contracts and add `crontab-service` when they
need persistence, Quartz scheduling, or the HTTP adapter. No dependency on trigger-service
or process-service is introduced.
