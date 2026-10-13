package org.chenile.trigger.store;

import org.chenile.trigger.model.TriggerLog;
import java.util.Optional;

/** Persistence for trigger idempotency claims and their dispatch outcomes. */
public interface TriggerLogStore {
    TriggerLog record(TriggerLog log);
    Optional<TriggerLog> findByTriggerIdAndEventName(String triggerId, String eventName);
    TriggerLog markCompleted(String logId);
    TriggerLog markFailed(String logId);
}
