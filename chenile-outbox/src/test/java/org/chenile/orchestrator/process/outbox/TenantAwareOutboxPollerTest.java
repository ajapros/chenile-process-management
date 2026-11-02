package org.chenile.orchestrator.process.outbox;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.chenile.core.context.ContextContainer;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Database-per-tenant outbox: each tenant has its own outbox table behind a tenant-routing
 * DataSource, configured like Chenile's multi-datasource-utils (route on the ContextContainer
 * tenant, no lenient fallback, tenant1 as the default).
 */
class TenantAwareOutboxPollerTest {
  private final Map<String, JdbcTemplate> tenantDatabases = new LinkedHashMap<>();
  private ProcessOutboxRepository repository;
  private TransactionTemplate transaction;
  private JdbcTemplate routedJdbc;
  private ContextContainer.ContextSnapshot original;

  @BeforeEach
  void setUp() throws Exception {
    original = ContextContainer.getInstance().snapshot();
    Map<Object, Object> targets = new LinkedHashMap<>();
    for (String tenant : List.of("tenant1", "tenant2")) {
      JdbcDataSource data = new JdbcDataSource();
      data.setURL(
          "jdbc:h2:mem:" + tenant + "_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
      try (var connection = data.getConnection()) {
        ScriptUtils.executeSqlScript(
            connection, new ClassPathResource("chenile-process-outbox-schema.sql"));
      }
      targets.put(tenant, data);
      tenantDatabases.put(tenant, new JdbcTemplate(data));
    }
    AbstractRoutingDataSource routing =
        new AbstractRoutingDataSource() {
          @Override
          protected Object determineCurrentLookupKey() {
            String tenant = ContextContainer.getInstance().getTenant();
            return tenant == null || tenant.isBlank() ? null : tenant.trim();
          }
        };
    routing.setTargetDataSources(targets);
    routing.setDefaultTargetDataSource(targets.get("tenant1"));
    routing.setLenientFallback(false);
    routing.afterPropertiesSet();
    DataSource routed = routing;
    var manager = new DataSourceTransactionManager(routed);
    transaction = new TransactionTemplate(manager);
    routedJdbc = new JdbcTemplate(routed);
    repository = new ProcessOutboxRepository(routedJdbc, manager);
  }

  @AfterEach
  void restoreContext() {
    ContextContainer.getInstance().restore(original);
  }

  @Test
  void schedulerThreadWithoutTenantOnlyDrainsTheDefaultTenant() {
    enqueueAs("tenant1", command("p1", "tenant1"));
    enqueueAs("tenant2", command("p2", "tenant2"));
    noTenant();

    new TenantAwareOutboxPoller(dispatcher(ignored -> {}), OutboxTenantSupplier.none()).poll();

    assertEquals(0L, pending("tenant1"));
    assertEquals(1L, pending("tenant2"), "the reported bug: tenant2's outbox is never drained");
  }

  @Test
  void drainsEveryTenantOnThatTenantsOwnDatabase() {
    enqueueAs("tenant1", command("p1", "tenant1"));
    enqueueAs("tenant2", command("p2a", "tenant2"));
    enqueueAs("tenant2", command("p2b", "tenant2"));
    noTenant();
    List<String> handled = new ArrayList<>();

    new TenantAwareOutboxPoller(
            dispatcher(
                c -> {
                  String tenant = ContextContainer.getInstance().getTenant();
                  // The handler's own reads go to the database that holds the row it is handling.
                  Long sameDatabase =
                      routedJdbc.queryForObject(
                          "select count(*) from chenile_process_outbox where id=?",
                          Long.class,
                          c.id);
                  handled.add(c.processId + "@" + tenant + ":" + sameDatabase);
                }),
            OutboxTenantSupplier.of(List.of("tenant1", "tenant2")))
        .poll();

    assertEquals(List.of("p1@tenant1:1", "p2a@tenant2:1", "p2b@tenant2:1"), handled);
    assertEquals(1L, done("tenant1"));
    assertEquals(2L, done("tenant2"));
    assertEquals(0L, pending("tenant1") + pending("tenant2"));
    assertEquals("", ContextContainer.getInstance().getTenant(), "scheduler context restored");
  }

  @Test
  void aBrokenTenantDoesNotStopTheOthers() {
    enqueueAs("tenant2", command("p2", "tenant2"));
    noTenant();
    List<String> handled = new ArrayList<>();

    // "unknown" has no DataSource, so routing fails for it, exactly as in multi-datasource-utils.
    new TenantAwareOutboxPoller(
            dispatcher(c -> handled.add(c.processId)),
            OutboxTenantSupplier.of(List.of("unknown", "tenant2")))
        .poll();

    assertEquals(List.of("p2"), handled);
    assertEquals(0L, pending("tenant2"));
  }

  @Test
  void aRowWithoutTenantKeepsThePollingTenant() {
    enqueueAs("tenant2", command("untagged", null));
    noTenant();
    List<String> seen = new ArrayList<>();

    new TenantAwareOutboxPoller(
            dispatcher(c -> seen.add(ContextContainer.getInstance().getTenant())),
            OutboxTenantSupplier.of(List.of("tenant2")))
        .poll();

    assertEquals(List.of("tenant2"), seen);
    assertEquals(1L, done("tenant2"));
  }

  @Test
  void restoresTheCallersContextAfterEachTenant() {
    var context = ContextContainer.getInstance();
    context.clear();
    context.setTenant("caller");
    context.put("x-caller-only", "kept");

    new TenantAwareOutboxPoller(
            dispatcher(ignored -> {}), OutboxTenantSupplier.of(List.of("tenant1", "tenant2")))
        .poll();

    assertEquals("caller", context.getTenant());
    assertEquals("kept", context.get("x-caller-only"));
  }

  @Test
  void blankAndDuplicateTenantsAreIgnored() {
    var poller =
        new TenantAwareOutboxPoller(
            dispatcher(ignored -> {}),
            () -> java.util.Arrays.asList(" tenant1 ", "", null, "tenant1", "tenant2"));
    assertEquals(List.of("tenant1", "tenant2"), poller.tenants());
  }

  private OutboxCommand command(String process, String tenant) {
    OutboxCommand command = OutboxTestSupport.emitCompleted(process, "{}");
    command.tenantId = tenant;
    return command;
  }

  private void enqueueAs(String tenant, OutboxCommand command) {
    var context = ContextContainer.getInstance();
    context.clear();
    context.setTenant(tenant);
    transaction.executeWithoutResult(tx -> assertTrue(repository.enqueue(command)));
  }

  private void noTenant() {
    ContextContainer.getInstance().clear();
  }

  private long pending(String tenant) {
    return count(tenant, "PENDING");
  }

  private long done(String tenant) {
    return count(tenant, "DONE");
  }

  private long count(String tenant, String status) {
    return tenantDatabases
        .get(tenant)
        .queryForObject(
            "select count(*) from chenile_process_outbox where status=?", Long.class, status);
  }

  private OutboxDispatcher dispatcher(Consumer<OutboxCommand> dispatch) {
    var registry =
        new OutboxCommandRegistry(
            List.of(OutboxTestSupport.command("EMIT_COMPLETED", dispatch, ignored -> {})));
    OutboxRetryPolicy policy = new OutboxRetryPolicy();
    policy.initialIntervalMillis = 0L;
    policy.maxAttempts = 2;
    return new OutboxDispatcher(repository, registry, policy, "tenant-worker", 300);
  }
}
