package org.chenile.orchestrator.process.configuration;

import java.time.Duration;
import org.chenile.orchestrator.process.outbox.*;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableScheduling
public class ProcessOutboxConfiguration {
  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(
      OutboxCommandRegistry.class)
  OutboxCommandRegistry outboxCommandRegistry(java.util.List<OutboxCommandLifecycle<?>> commands) {
    return new OutboxCommandRegistry(commands);
  }

  @Bean
  ProcessOutboxRepository processOutboxRepository(
      JdbcTemplate jdbc, PlatformTransactionManager manager) {
    return new ProcessOutboxRepository(jdbc, manager);
  }

  @Bean
  OutboxDispatcher outboxDispatcher(
      ProcessOutboxRepository repository,
      OutboxCommandRegistry commands,
      @Value("${chenile.process.outbox.worker-id:${HOSTNAME:local-outbox}}") String workerId,
      @Value("${chenile.process.outbox.lock-seconds:300}") int lockSeconds,
      @Value("${chenile.process.outbox.max-attempts:5}") int attempts) {
    OutboxRetryPolicy policy = OutboxRetryPolicy.defaults();
    policy.maxAttempts = attempts;
    return new OutboxDispatcher(repository, commands, policy, workerId, lockSeconds);
  }

  /** Bean name of the tenant to DataSource map published by Chenile's multi-datasource-utils. */
  public static final String MULTI_TENANT_DATA_SOURCES = "multiTenantTargetDataSources";

  /**
   * Polls every tenant's outbox. Tenants come from, in order: an application {@link
   * OutboxTenantSupplier} bean; {@code chenile.process.outbox.tenants} (comma separated); the keys
   * of the {@value #MULTI_TENANT_DATA_SOURCES} map when multi-datasource-utils is in use; otherwise
   * none, which keeps single-tenant polling unchanged.
   */
  @Bean
  TenantAwareOutboxPoller tenantAwareOutboxPoller(
      OutboxDispatcher dispatcher,
      ObjectProvider<OutboxTenantSupplier> applicationTenants,
      @Value("${chenile.process.outbox.tenants:}") String configuredTenants,
      BeanFactory beans) {
    OutboxTenantSupplier supplier = applicationTenants.getIfUnique();
    if (supplier == null) supplier = defaultTenantSupplier(configuredTenants, beans);
    return new TenantAwareOutboxPoller(dispatcher, supplier);
  }

  static OutboxTenantSupplier defaultTenantSupplier(String configuredTenants, BeanFactory beans) {
    var configured =
        OutboxTenantSupplier.clean(
            configuredTenants == null
                ? java.util.List.of()
                : java.util.Arrays.asList(configuredTenants.split(",")));
    if (!configured.isEmpty()) return OutboxTenantSupplier.of(configured);
    if (beans.containsBean(MULTI_TENANT_DATA_SOURCES)) {
      // Looked up per cycle (a cheap map read) so a refreshed tenant map is picked up.
      return () ->
          OutboxTenantSupplier.clean(
              beans.getBean(MULTI_TENANT_DATA_SOURCES, java.util.Map.class).keySet());
    }
    return OutboxTenantSupplier.none();
  }

  @Bean
  @ConditionalOnProperty(
      name = "chenile.process.outbox.run-dispatcher",
      havingValue = "true",
      matchIfMissing = true)
  SchedulingConfigurer processOutboxScheduling(
      TenantAwareOutboxPoller poller,
      @Value("${chenile.process.outbox.poll-interval-millis:1000}") long intervalMillis) {
    if (intervalMillis < 1)
      throw new IllegalArgumentException("Positive outbox poll interval required");
    return tasks -> tasks.addFixedDelayTask(poller::poll, Duration.ofMillis(intervalMillis));
  }
}
