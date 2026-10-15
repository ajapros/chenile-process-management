package org.chenile.trigger;

import org.chenile.trigger.model.TriggerInput;

/** Optional dispatch observation; persisted trigger lifecycle does not depend on this extension. */
public interface TriggerObserver {
    TriggerObserver NONE = new TriggerObserver() {};
    default void started(String executionId, TriggerInput input) {}
    default void finished(String executionId, RuntimeException failure) {}
}
