package org.chenile.orchestrator.process.service.impl;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.util.UUID;

/** Runs the same JPA + JDBC atomicity and chaining assertions against real PostgreSQL. */
@EnabledIfSystemProperty(named="chenile.outbox.test.jdbc-url", matches=".+")
class PostgresDurableProcessIntegrationTest extends DurableProcessIntegrationTest {
    static String schema;
    static DriverManagerDataSource admin;

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String url = System.getProperty("chenile.outbox.test.jdbc-url");
        String user = System.getProperty("chenile.outbox.test.username", System.getProperty("user.name"));
        String password = System.getProperty("chenile.outbox.test.password", "");
        admin = new DriverManagerDataSource(url, user, password);
        schema = "process_test_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(admin).execute("create schema " + schema);
        properties.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema);
        properties.add("spring.datasource.username", () -> user);
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @AfterAll static void cleanUpSchema() {
        if (schema != null) new JdbcTemplate(admin).execute("drop schema " + schema + " cascade");
    }
}
