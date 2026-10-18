package org.chenile.trigger.cron;

import org.chenile.configuration.crontab.CrontabTriggerConfiguration;
import org.chenile.trigger.TriggerService;
import org.chenile.trigger.model.TriggerResult;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CrontabTriggerConfigurationTest {
    @Test void wiresCronWithAnApplicationDefinedTriggerService() {
        TriggerService triggers = org.mockito.Mockito.mock(TriggerService.class);
        new ApplicationContextRunner()
                .withUserConfiguration(CrontabTriggerConfiguration.class)
                .withBean(TriggerService.class, () -> triggers)
                .withBean(Scheduler.class, () -> mock(Scheduler.class))
                .withBean(CrontabRepository.class, () -> mock(CrontabRepository.class))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertSame(triggers, context.getBean(TriggerService.class));
                    assertNotNull(context.getBean(CrontabScheduler.class));
                    assertNotNull(context.getBean(CrontabService.class));
                    assertFalse(context.containsBean("triggerLogStore"));
                });
    }
}
