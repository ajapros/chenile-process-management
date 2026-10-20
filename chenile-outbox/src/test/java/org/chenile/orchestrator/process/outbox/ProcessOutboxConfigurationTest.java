package org.chenile.orchestrator.process.outbox;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Date;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.chenile.orchestrator.process.configuration.ProcessOutboxConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

class ProcessOutboxConfigurationTest {
  private static final DriverManagerDataSource DATA_SOURCE = dataSource();

  private final ApplicationContextRunner application =
      new ApplicationContextRunner()
          .withUserConfiguration(ProcessOutboxConfiguration.class)
          .withBean(JdbcTemplate.class, () -> new JdbcTemplate(DATA_SOURCE))
          .withBean(
              PlatformTransactionManager.class, () -> new DataSourceTransactionManager(DATA_SOURCE))
          .withBean(OutboxCommandRegistry.class, () -> new OutboxCommandRegistry(List.of()))
          // A long poll interval prevents a scheduled execution during these short wiring checks.
          .withBean(
              TaskScheduler.class, NoOpTaskScheduler::new);

  private static DriverManagerDataSource dataSource() {
    var source = new DriverManagerDataSource();
    source.setDriverClassName("org.h2.Driver");
    source.setUrl("jdbc:h2:mem:outbox-config;DB_CLOSE_DELAY=-1");
    source.setUsername("sa");
    source.setPassword("");
    return source;
  }

  private static final class NoOpTaskScheduler implements TaskScheduler {
    @Override
    public ScheduledFuture<?> schedule(Runnable task, org.springframework.scheduling.Trigger trigger) {
      return new NeverScheduledFuture();
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
      return new NeverScheduledFuture();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
        Runnable task, Instant startTime, Duration period) {
      return new NeverScheduledFuture();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
      return new NeverScheduledFuture();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable task, Instant startTime, Duration delay) {
      return new NeverScheduledFuture();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
      return new NeverScheduledFuture();
    }
  }

  private static final class NeverScheduledFuture implements ScheduledFuture<Object> {
    @Override
    public long getDelay(java.util.concurrent.TimeUnit unit) {
      return Long.MAX_VALUE;
    }

    @Override
    public int compareTo(Delayed other) {
      return 1;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return true;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public boolean isDone() {
      return false;
    }

    @Override
    public Object get() {
      return null;
    }

    @Override
    public Object get(long timeout, java.util.concurrent.TimeUnit unit) {
      return null;
    }
  }

  @Test
  void alwaysWiresTheOutboxAndAllowsManualDispatchWithoutBackgroundPolling() {
    application
        .withPropertyValues("chenile.process.outbox.run-dispatcher=false")
        .run(
            context -> {
              assertNull(context.getStartupFailure());
              assertNotNull(context.getBean(ProcessOutboxRepository.class));
              assertNotNull(context.getBean(OutboxDispatcher.class));
              assertTrue(context.getBean(ScheduledTaskHolder.class).getScheduledTasks().isEmpty());
            });
  }

  @Test
  void enabledPollingUsesConfiguredDelay() {
    application
        .withPropertyValues("chenile.process.outbox.poll-interval-millis=86400000")
        .run(
            context -> {
              assertNull(context.getStartupFailure());
              assertNotNull(context.getBean(ProcessOutboxRepository.class));
              assertNotNull(context.getBean(OutboxDispatcher.class));
              var tasks = context.getBean(ScheduledTaskHolder.class).getScheduledTasks();
              assertEquals(1, tasks.size());
              var task = assertInstanceOf(FixedDelayTask.class, tasks.iterator().next().getTask());
              assertEquals(Duration.ofDays(1), task.getIntervalDuration());
            });

  }
}
