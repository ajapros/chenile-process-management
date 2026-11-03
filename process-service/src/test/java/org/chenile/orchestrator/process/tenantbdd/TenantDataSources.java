package org.chenile.orchestrator.process.tenantbdd;

import static org.chenile.orchestrator.process.configuration.ProcessOutboxConfiguration.MULTI_TENANT_DATA_SOURCES;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.chenile.core.context.ContextContainer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * A database per tenant, set up the way Chenile's multi-datasource-utils sets it up: a {@code
 * multiTenantTargetDataSources} map of tenant pools and a primary routing DataSource that picks the
 * pool from the {@link ContextContainer} tenant, falling back to {@value #DEFAULT_TENANT} when there
 * is no tenant and failing for an unknown one.
 *
 * <p>multi-datasource-utils itself is not used because its configuration is unconditional: on the
 * test classpath it would be component-scanned into every other process-service test context and
 * fail them for lack of {@code chenile.multids.datasources}. This package is outside every other
 * test context's scan.
 */
@Configuration
public class TenantDataSources {
  static final List<String> TENANTS = List.of("tenant1", "tenant2");
  static final String DEFAULT_TENANT = "tenant1";

  @Bean(MULTI_TENANT_DATA_SOURCES)
  Map<String, DataSource> multiTenantTargetDataSources() throws SQLException {
    Map<String, DataSource> tenants = new LinkedHashMap<>();
    for (String tenant : TENANTS) {
      HikariConfig config = new HikariConfig();
      config.setPoolName("tenant-outbox-bdd-" + tenant);
      config.setJdbcUrl(
          "jdbc:h2:mem:tenant-outbox-bdd-"
              + tenant
              + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
      config.setUsername("sa");
      config.setPassword("");
      config.setMaximumPoolSize(10);
      HikariDataSource pool = new HikariDataSource(config);
      // Each tenant database gets its own outbox, as a migration tool would provision it.
      try (Connection connection = pool.getConnection()) {
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("reset-test-outbox.sql"));
        ScriptUtils.executeSqlScript(
            connection, new ClassPathResource("chenile-process-outbox-schema.sql"));
      }
      tenants.put(tenant, pool);
    }
    return tenants;
  }

  @Bean
  @Primary
  DataSource dataSource() throws SQLException {
    Map<String, DataSource> tenants = multiTenantTargetDataSources();
    // The startup thread must not carry a tenant left behind by an earlier test.
    ContextContainer.getInstance().clear();
    AbstractRoutingDataSource routing =
        new AbstractRoutingDataSource() {
          @Override
          protected Object determineCurrentLookupKey() {
            String tenant = ContextContainer.getInstance().getTenant();
            return tenant == null || tenant.isBlank() ? null : tenant.trim();
          }
        };
    routing.setTargetDataSources(new LinkedHashMap<>(tenants));
    routing.setDefaultTargetDataSource(tenants.get(DEFAULT_TENANT));
    routing.setLenientFallback(false);
    return routing;
  }

  /**
   * Hibernate's ddl-auto only reaches the default tenant's database, so the JPA tables of every
   * other tenant are created here, with that tenant set so the routing DataSource reaches it. This
   * runs after all singletons exist and before the outbox poller is scheduled.
   */
  @Bean
  SmartInitializingSingleton tenantJpaSchemas(ObjectProvider<EntityManagerFactory> factory) {
    return () -> {
      var context = ContextContainer.getInstance();
      var saved = context.snapshot();
      try {
        for (String tenant : TENANTS) {
          if (tenant.equals(DEFAULT_TENANT)) continue;
          context.clear();
          context.setTenant(tenant);
          factory.getObject().getSchemaManager().create(true);
        }
      } finally {
        context.restore(saved);
      }
    };
  }
}
