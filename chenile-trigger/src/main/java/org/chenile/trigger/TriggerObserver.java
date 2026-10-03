package org.chenile.trigger;

import org.chenile.trigger.model.TriggerInput;

/** Execution history is deliberately separate from trigger idempotency. */
public interface TriggerObserver {
    TriggerObserver NONE = new TriggerObserver() {};
    default void started(String executionId, TriggerInput input) {}
    default void finished(String executionId, RuntimeException failure) {}
}
