# Trigger API

`trigger-api` provides `TriggerService`, `TriggerObserver`, `TriggerInput`, and
`TriggerResult`. It has no runtime dependencies on Spring, persistence, Quartz,
or the process manager. Java package names remain `org.chenile.trigger` and
`org.chenile.trigger.model`.

Adapters depend on this contract. Applications choose a `TriggerService`
implementation; `trigger-service` supplies Chenile's event dispatcher.
