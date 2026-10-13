package org.chenile.trigger;

import org.chenile.trigger.model.TriggerInput;
import org.chenile.trigger.model.TriggerResult;

public interface TriggerService {
    TriggerResult trigger(TriggerInput input);
}
