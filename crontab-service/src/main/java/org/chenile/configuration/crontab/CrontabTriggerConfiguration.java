package org.chenile.configuration.crontab;

import java.util.Optional;
import org.springframework.context.annotation.Import;
import org.chenile.configuration.crontab.controller.CrontabController;
import org.chenile.scheduler.model.ScheduledExecutionRecord;
import org.chenile.scheduler.model.ScheduledExecutionRequest;
import org.chenile.scheduler.store.SchedulerExecutionStore;
import org.chenile.trigger.TriggerService;
import org.chenile.trigger.cron.*;
import org.quartz.Scheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Cron adapter wiring. The host supplies Scheduler, TriggerService and persistence discovery. */
@Configuration
@Import(CrontabController.class)
public class CrontabTriggerConfiguration {
    @Bean @ConditionalOnMissingBean(SchedulerExecutionStore.class)
    public SchedulerExecutionStore inMemorySchedulerExecutionStore() {
        return new SchedulerExecutionStore() {
            @Override public boolean tryStartExecution(ScheduledExecutionRequest request) { return true; }
            @Override public void markSuccess(String id, int attempt, String metadata) { }
            @Override public void markFailure(String id, int attempt, String error) { }
            @Override public void markTimedOut(String id, int attempt, String error) { }
            @Override public Optional<ScheduledExecutionRecord> findByExecutionId(String id) { return Optional.empty(); }
        };
    }

    @Bean
    public CrontabScheduler crontabScheduler(CrontabRepository repository, TriggerService triggerService,
                                            Scheduler quartzScheduler) {
        return new CrontabScheduler(repository, triggerService, quartzScheduler);
    }

    @Bean(name = {"_crontabService_", "crontabService"})
    public CrontabService crontabService(CrontabRepository repository, CrontabScheduler scheduler) {
        return new DatabaseCrontabService(repository, scheduler);
    }
}
