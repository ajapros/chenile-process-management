package org.chenile.orchestrator.process.configuration.management;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.*;

/** Runs the real Spring MVC/API contracts and migration against an isolated PostgreSQL schema. */
@org.springframework.boot.test.context.SpringBootTest(
    classes = org.chenile.orchestrator.process.SpringTestConfig.class,
    properties = {
      "chenile.process.management.enabled=true",
      "chenile.process.configurator=database",
      "chenile.process.management.api-key=integration-test-administrator-key-123456"
    })
@EnabledIfSystemProperty(named = "chenile.outbox.test.jdbc-url", matches = ".+")
class PostgresProcessManagementApiIntegrationTest
    extends AbstractProcessManagementApiIntegrationTest {
  static String schema;
  static DriverManagerDataSource admin;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    String url = System.getProperty("chenile.outbox.test.jdbc-url");
    String user =
        System.getProperty("chenile.outbox.test.username", System.getProperty("user.name"));
    String password = System.getProperty("chenile.outbox.test.password", "");
    admin = new DriverManagerDataSource(url, user, password);
    schema = "management_test_" + UUID.randomUUID().toString().replace("-", "");
    new JdbcTemplate(admin).execute("create schema " + schema);
    properties.add(
        "spring.datasource.url",
        () -> url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema);
    properties.add("spring.datasource.username", () -> user);
    properties.add("spring.datasource.password", () -> password);
    properties.add("spring.jpa.hibernate.ddl-auto", () -> "create");
  }

  @Test
  void historyMigrationCreatesTablesAndIsRerunnable() throws Exception {
    // Only drop test-owned tables inside this test's randomly allocated schema.
    var migration =
        new ResourceDatabasePopulator(
            new ClassPathResource("chenile-process-management-history-schema.sql"));
    migration.execute(jdbc.getDataSource());
    migration.execute(jdbc.getDataSource());
    definition("source", true, "");
    generate("alpha", "migration", "source");
    dispatcher.drainAll(30);
    var root = processes.findAll().get(0);
    complete(root.id, "alpha");
    assertEquals(
        1,
        executions
            .findCommands(
                org.chenile.trigger.DispatchTriggerCommand.TYPE, "alpha", null, null, null, 0, 10)
            .totalElements());
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from information_schema.tables where table_schema=current_schema() and"
                + " table_name='chenile_process_completion_event'",
            Integer.class));
    assertEquals(
        1,
        response(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    BASE + "/executions"),
                "alpha",
                200)
            .get("totalElements")
            .asInt());
  }

  @AfterAll
  static void cleanUpSchema() {
    if (schema != null) new JdbcTemplate(admin).execute("drop schema " + schema + " cascade");
  }
}
