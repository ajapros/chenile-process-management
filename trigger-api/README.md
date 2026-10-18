# Trigger API

`trigger-api` provides TriggerService, TriggerInput, TriggerResult (acceptance ID only), and
TriggerStatus. It has no runtime dependency on Spring, persistence, cron or process management.

Adapters supply an optional idempotencyKey, eventId, payload, x-prefixed headers, triggerContext
and triggerTime. The implementation assigns the trigger ID. TriggerService.trigger returns only
that durable acknowledgement; TriggerService.status queries it in the current tenant context.
The standard implementation in trigger-service uses the outbox ID as the trigger ID and executes
later through Chenile's event transformation pipeline.
