package org.chenile.orchestrator.process.tenantbdd;

import static org.chenile.orchestrator.process.configuration.ProcessOutboxConfiguration.MULTI_TENANT_DATA_SOURCES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.spring.CucumberContextConfiguration;
import java.util.Map;
import javax.sql.DataSource;
import org.chenile.orchestrator.process.test.SharedData;
import org.chenile.orchestrator.process.test.SpringConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Process management on a database per tenant, with the outbox drained only by the background
 * poller. Unlike the other suites, nothing here calls the dispatcher: work completes only if the
 * scheduled poller reaches every tenant's outbox.
 */
@CucumberContextConfiguration
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    classes = {SpringConfig.class, TenantDataSources.class},
    properties = {
      "chenile.process.outbox.run-dispatcher=true",
      "chenile.process.outbox.poll-interval-millis=100",
      "spring.sql.init.mode=never",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
@AutoConfigureMockMvc
@ActiveProfiles("unittest")
public class CukesSteps {
  @Autowired ApplicationContext application;

  @Before
  public void synchronousWorkers() {
    SharedData.SYNCH_MODE = true;
  }

  /**
   * Waits for the poller: the tenant's outbox must have received work, and all of it must be done.
   */
  @Then("the process outbox of tenant {string} is drained within {int} seconds")
  public void outboxDrained(String tenant, int seconds) throws InterruptedException {
    JdbcTemplate outbox = outbox(tenant);
    long deadline = System.currentTimeMillis() + seconds * 1000L;
    long waiting = count(outbox, "status in ('PENDING', 'RUNNING')");
    while (waiting > 0 && System.currentTimeMillis() < deadline) {
      Thread.sleep(100);
      waiting = count(outbox, "status in ('PENDING', 'RUNNING')");
    }
    assertTrue(
        "Tenant " + tenant + "'s outbox received no commands", count(outbox, "1 = 1") > 0);
    assertEquals(
        "Commands still waiting in tenant " + tenant + "'s outbox after " + seconds + " seconds",
        0L,
        waiting);
    assertEquals("DEAD commands in tenant " + tenant + "'s outbox", 0L, count(outbox, "status = 'DEAD'"));
  }

  private JdbcTemplate outbox(String tenant) {
    @SuppressWarnings("unchecked")
    Map<String, DataSource> tenants = application.getBean(MULTI_TENANT_DATA_SOURCES, Map.class);
    DataSource database = tenants.get(tenant);
    assertNotNull("No database for tenant " + tenant, database);
    return new JdbcTemplate(database);
  }

  private static long count(JdbcTemplate outbox, String condition) {
    Long count =
        outbox.queryForObject(
            "select count(*) from chenile_process_outbox where " + condition, Long.class);
    return count == null ? 0L : count;
  }
}
