package org.chenile.orchestrator.process.outbox;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.chenile.orchestrator.process.configuration.ProcessOutboxConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessOutboxConfigurationTest {
    private final ApplicationContextRunner application = new ApplicationContextRunner()
            .withUserConfiguration(ProcessOutboxConfiguration.class)
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withBean(OutboxCommandRegistry.class, () -> new OutboxCommandRegistry<>(List.of()))
            // Prevent real background execution; invoke the registered task explicitly below.
            .withBean(TaskScheduler.class, () -> mock(TaskScheduler.class));

    @Test void pollingIsAbsentWhenOutboxOrBackgroundDispatchIsDisabled() {
        application.run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.getBeansOfType(ProcessOutboxRepository.class).isEmpty());
            assertTrue(context.getBeansOfType(OutboxDispatcher.class).isEmpty());
            assertTrue(context.getBeansOfType(ScheduledTaskHolder.class).isEmpty());
        });
        application.withPropertyValues("chenile.process.outbox.enabled=true",
                "chenile.process.outbox.run-dispatcher=false").run(context -> {
            assertNull(context.getStartupFailure());
            assertNotNull(context.getBean(ProcessOutboxRepository.class));
            assertNotNull(context.getBean(OutboxDispatcher.class));
            assertTrue(context.getBean(ScheduledTaskHolder.class).getScheduledTasks().isEmpty());
        });
    }

    @Test void enabledPollingUsesConfiguredDelayAndDispatchesThroughTheDispatcher() {
        application.withPropertyValues("chenile.process.outbox.enabled=true",
                "chenile.process.outbox.poll-interval-millis=2500").run(context -> {
            assertNull(context.getStartupFailure());
            var tasks = context.getBean(ScheduledTaskHolder.class).getScheduledTasks();
            assertEquals(1, tasks.size());
            var task = assertInstanceOf(FixedDelayTask.class, tasks.iterator().next().getTask());
            assertEquals(Duration.ofMillis(2500), task.getIntervalDuration());
        });

        var repository = mock(ProcessOutboxRepository.class);
        when(repository.claimNext(anyString(), anyInt(), anyInt())).thenReturn(Optional.empty());
        var dispatcher = new OutboxDispatcher(repository, new OutboxCommandRegistry<>(List.of()),
                OutboxRetryPolicy.defaults(), "test-worker", 300);
        dispatcher.poll();
        verify(repository).claimNext(eq("test-worker"), eq(300), anyInt());
        verify(repository).deadCount();
    }
}
