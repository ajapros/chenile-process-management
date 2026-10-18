# Crontab trigger

`crontab-trigger` owns the crontab entity, repository, JSON reader, validation,
transactional management service, and Quartz adapter. It depends on `trigger-api`
and does not depend on `trigger-service` or `process-service`.

The application supplies a `TriggerService` bean. It may use `trigger-service`
or its own implementation. Scan `org.chenile.configuration.crontab` or import
`CrontabTriggerConfiguration` to wire cron support with a Quartz `Scheduler`.
The application owns entity scanning of `org.chenile.trigger.cron` and repository
scanning of the same package; framework configuration does not declare scans.

Crontabs retain their table, JSON format, Java package names and Quartz job keys.
Each firing supplies a stable submission idempotency key of the form
`crontab:{crontabId}:{quartzFireInstanceId}`. Duplicate handling belongs to the
chosen trigger service implementation. The acknowledgement supplies the assigned outbox/trigger ID.
Cron identity, Quartz timing and generated/configured x-headers go into triggerContext metadata.
The event payload string is passed unchanged; subscriber conversion belongs to Chenile. See [trigger-service](../trigger-service/README.md)
for Chenile's dispatch and idempotency behavior.
