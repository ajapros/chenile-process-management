package org.chenile.orchestrator.process.configuration;

import org.chenile.orchestrator.process.outbox.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import java.time.Duration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableScheduling
public class ProcessOutboxConfiguration {
    @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(OutboxCommandRegistry.class)
    OutboxCommandRegistry outboxCommandRegistry(java.util.List<OutboxCommandLifecycle<?>> commands) {
        return new OutboxCommandRegistry(commands);
    }

    @Bean ProcessOutboxRepository processOutboxRepository(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        return new ProcessOutboxRepository(jdbc, manager);
    }

    @Bean OutboxDispatcher outboxDispatcher(ProcessOutboxRepository repository, OutboxCommandRegistry commands,
            @Value("${chenile.process.outbox.worker-id:${HOSTNAME:local-outbox}}") String workerId,
            @Value("${chenile.process.outbox.lock-seconds:300}") int lockSeconds,
            @Value("${chenile.process.outbox.max-attempts:5}") int attempts) {
        OutboxRetryPolicy policy = OutboxRetryPolicy.defaults(); policy.maxAttempts = attempts;
        return new OutboxDispatcher(repository, commands, policy, workerId, lockSeconds);
    }

    @Bean
    @ConditionalOnProperty(name = "chenile.process.outbox.run-dispatcher", havingValue = "true", matchIfMissing = true)
    SchedulingConfigurer processOutboxScheduling(OutboxDispatcher dispatcher,
            @Value("${chenile.process.outbox.poll-interval-millis:1000}") long intervalMillis) {
        if (intervalMillis < 1) throw new IllegalArgumentException("Positive outbox poll interval required");
        return tasks -> tasks.addFixedDelayTask(dispatcher::poll, Duration.ofMillis(intervalMillis));
    }
}
