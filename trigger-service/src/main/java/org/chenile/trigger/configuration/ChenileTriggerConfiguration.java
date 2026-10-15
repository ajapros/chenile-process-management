package org.chenile.trigger.configuration;

import org.chenile.core.event.EventProcessor;
import org.chenile.core.model.ChenileConfiguration;
import org.chenile.trigger.store.*;
import org.chenile.trigger.EventTriggerService;
import org.chenile.trigger.TriggerService;
import org.chenile.trigger.TriggerObserver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Configuration
public class ChenileTriggerConfiguration {
    @Bean public TriggerLogStore triggerLogStore(TriggerLogRepository repository) {
        return new DatabaseTriggerLogStore(repository);
    }
    @Bean public TriggerService triggerService(TriggerLogStore triggerLogStore, EventProcessor eventProcessor,
                                               ChenileConfiguration configuration, TriggerObserver observer) {
        return new EventTriggerService(triggerLogStore, eventProcessor, configuration, observer);
    }
    @Bean @ConditionalOnMissingBean(TriggerObserver.class)
    public TriggerObserver triggerObserver() {
        return TriggerObserver.NONE;
    }
}
