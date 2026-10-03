package org.chenile.orchestrator.process.configuration;

import org.chenile.orchestrator.process.outbox.*;
import org.chenile.orchestrator.process.service.outbox.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "chenile.process.outbox.enabled", havingValue = "true")
public class ProcessOutboxConfiguration {
    @Bean ProcessOutboxRepository processOutboxRepository(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        return new ProcessOutboxRepository(jdbc, manager);
    }

    @Bean OutboxDispatcher outboxDispatcher(ProcessOutboxRepository repository, OutboxCommandRegistry<ProcessTransition> commands,
            @Value("${chenile.process.outbox.worker-id:${HOSTNAME:local-outbox}}") String workerId,
            @Value("${chenile.process.outbox.lock-seconds:300}") int lockSeconds,
            @Value("${chenile.process.outbox.max-attempts:5}") int attempts) {
        OutboxRetryPolicy policy = OutboxRetryPolicy.defaults(); policy.maxAttempts = attempts;
        return new OutboxDispatcher(repository, commands, policy, workerId, lockSeconds);
    }

    @Bean @ConditionalOnProperty(name = "chenile.process.outbox.run-dispatcher", havingValue = "true", matchIfMissing = true)
    Poller processOutboxPoller(OutboxDispatcher dispatcher, ProcessOutboxRepository repository) { return new Poller(dispatcher, repository); }

    static class Poller {
        private static final Logger LOG = LoggerFactory.getLogger(Poller.class);
        private final OutboxDispatcher dispatcher;
        private final ProcessOutboxRepository repository;
        Poller(OutboxDispatcher dispatcher, ProcessOutboxRepository repository) { this.dispatcher = dispatcher; this.repository = repository; }
        @Scheduled(fixedDelayString = "${chenile.process.outbox.poll-interval-millis:1000}")
        public void poll() {
            dispatcher.drainAll(100);
            long dead = repository.deadCount();
            if (dead > 0) LOG.error("Process outbox has {} DEAD commands requiring operator intervention", dead);
        }
    }
}
