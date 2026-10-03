package org.chenile.trigger.configuration;

import org.chenile.core.event.EventProcessor;
import org.chenile.core.model.ChenileConfiguration;
import org.chenile.trigger.store.*;
import org.chenile.trigger.EventTriggerService;
import org.chenile.trigger.TriggerService;
import org.chenile.trigger.TriggerObserver;
import org.chenile.trigger.cron.*;
import org.quartz.Scheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.chenile.scheduler.store.SchedulerExecutionStore;
import org.chenile.scheduler.model.ScheduledExecutionRequest;
import org.chenile.scheduler.model.ScheduledExecutionRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import java.util.Optional;

@Configuration
@EntityScan(basePackages = {"org.chenile.trigger.cron", "org.chenile.trigger.model"})
@EnableJpaRepositories(basePackages = {"org.chenile.trigger.cron", "org.chenile.trigger.store"})
public class ChenileTriggerConfiguration {
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
    @Bean public TriggerLogStore triggerLogStore(TriggerLogRepository repository) {
        return new DatabaseTriggerLogStore(repository);
    }
    @Bean public TriggerService triggerService(TriggerLogStore triggerLogStore, EventProcessor eventProcessor,
                                               ChenileConfiguration configuration, TriggerObserver observer) {
        return new EventTriggerService(triggerLogStore, eventProcessor, configuration, observer);
    }
    @Bean @ConditionalOnMissingBean(TriggerObserver.class)
    public TriggerObserver triggerObserver(TriggerExecutionRepository repository) {
        return new DatabaseTriggerObserver(repository);
    }
    @Bean @ConditionalOnBean(Scheduler.class)
    public CrontabScheduler crontabScheduler(CrontabRepository repository, TriggerService triggerService,
                                                   Scheduler quartzScheduler) {
        return new CrontabScheduler(repository, triggerService, quartzScheduler);
    }
    @Bean @ConditionalOnBean(CrontabScheduler.class)
    public CrontabService crontabService(CrontabRepository repository, CrontabScheduler scheduler) {
        return new DatabaseCrontabService(repository, scheduler);
    }
}
