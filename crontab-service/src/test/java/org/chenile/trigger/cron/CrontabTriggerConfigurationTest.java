package org.chenile.trigger.cron;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import org.chenile.configuration.crontab.CrontabTriggerConfiguration;
import org.chenile.trigger.TriggerService;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CrontabTriggerConfigurationTest {
  @Test
  void wiresCronWithAnApplicationDefinedTriggerService() {
    TriggerService triggers = org.mockito.Mockito.mock(TriggerService.class);
    new ApplicationContextRunner()
        .withUserConfiguration(CrontabTriggerConfiguration.class)
        .withBean(
            org.chenile.core.model.ChenileConfiguration.class,
            () -> mock(org.chenile.core.model.ChenileConfiguration.class))
        .withBean(
            org.chenile.owiz.OrchExecutor.class, () -> mock(org.chenile.owiz.OrchExecutor.class))
        .withBean(
            org.chenile.core.context.ChenileExchangeBuilder.class,
            () -> mock(org.chenile.core.context.ChenileExchangeBuilder.class))
        .withBean(
            org.chenile.core.entrypoint.ChenileEntryPoint.class,
            () -> mock(org.chenile.core.entrypoint.ChenileEntryPoint.class))
        .withBean(TriggerService.class, () -> triggers)
        .withBean(Scheduler.class, () -> mock(Scheduler.class))
        .withBean(CrontabRepository.class, () -> mock(CrontabRepository.class))
        .run(
            context -> {
              assertNull(context.getStartupFailure());
              assertSame(triggers, context.getBean(TriggerService.class));
              assertNotNull(context.getBean(CrontabScheduler.class));
              assertNotNull(context.getBean(CrontabService.class));
              assertFalse(context.containsBean("triggerLogStore"));
            });
  }
}
