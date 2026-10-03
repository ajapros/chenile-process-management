package org.chenile.orchestrator.process.configuration.management;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Runs the real Spring MVC/API contracts and migration against an isolated PostgreSQL schema. */
@EnabledIfSystemProperty(named = "chenile.outbox.test.jdbc-url", matches = ".+")
class PostgresProcessManagementApiIntegrationTest extends DurableProcessManagementApiIntegrationTest {
    static String schema;
    static DriverManagerDataSource admin;
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String url = System.getProperty("chenile.outbox.test.jdbc-url");
        String user = System.getProperty("chenile.outbox.test.username", System.getProperty("user.name"));
        String password = System.getProperty("chenile.outbox.test.password", "");
        admin = new DriverManagerDataSource(url, user, password);
        schema = "management_test_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(admin).execute("create schema " + schema);
        properties.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema);
        properties.add("spring.datasource.username", () -> user);
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }
    @Test void historyMigrationCreatesTablesAndIsRerunnable() throws Exception {
        // Only drop test-owned tables inside this test's randomly allocated schema.
        jdbc.execute("drop table chenile_trigger_execution");
        jdbc.execute("drop table chenile_process_completion_event");
        var migration = new ResourceDatabasePopulator(new ClassPathResource("chenile-process-management-history-schema.sql"));
        migration.execute(jdbc.getDataSource()); migration.execute(jdbc.getDataSource());
        definition("source", true, ""); generate("alpha", "migration", "source");
        var root = processes.findAll().get(0); complete(root.id, "alpha");
        assertEquals(1, executions.count()); assertEquals(1, completions.count());
        assertEquals(1, response(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(BASE + "/executions"), "alpha", 200).get("totalElements").asInt());
    }
    @AfterAll static void cleanUpSchema() {
        if (schema != null) new JdbcTemplate(admin).execute("drop schema " + schema + " cascade");
    }
}
