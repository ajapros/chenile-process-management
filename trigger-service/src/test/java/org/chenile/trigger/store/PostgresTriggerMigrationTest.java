package org.chenile.trigger.store;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Every migration test owns a randomly named schema; existing application tables are never touched. */
@EnabledIfSystemProperty(named = "chenile.outbox.test.jdbc-url", matches = ".+")
class PostgresTriggerMigrationTest {
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private SingleConnectionDataSource source;
    private static String migrationSql;

    @BeforeAll static void loadMigration() throws Exception {
        migrationSql = new ClassPathResource("chenile-trigger-unification.sql").getContentAsString(StandardCharsets.UTF_8);
    }
    private String schema;

    @BeforeEach void createSchema() {
        String url = System.getProperty("chenile.outbox.test.jdbc-url");
        String user = System.getProperty("chenile.outbox.test.username", System.getProperty("user.name"));
        String password = System.getProperty("chenile.outbox.test.password", "");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        schema = "trigger_migration_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create schema " + schema);
        source = new SingleConnectionDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema, user, password, true);
        jdbc = new JdbcTemplate(source);
    }

    @AfterEach void cleanup() {
        try {
            if (source != null) source.destroy();
        } finally {
            if (schema != null) admin.execute("drop schema " + schema + " cascade");
        }
    }

    private void migrate() {
        new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(tx -> jdbc.execute(migrationSql));
    }

    private void legacyTables() {
        jdbc.execute("create table trigger_log (id varchar(255) primary key, trigger_id varchar(255) not null, event_name varchar(255) not null, trigger_time timestamptz not null, status varchar(255) not null, unique(trigger_id,event_name))");
        jdbc.execute("create table chenile_trigger_execution (id varchar(255) primary key, trigger_id varchar(255), event_name varchar(255), trigger_time timestamptz, tenant varchar(255), source varchar(255), crontab_id varchar(255), started_at timestamptz, finished_at timestamptz, status varchar(255), error text, payload text)");
    }

    private void claim(String id, String key, String status) {
        jdbc.update("insert into trigger_log values (?, ?, 'Event', '2026-10-14T00:00:00Z', ?)", id, key, status);
    }

    private void execution(String id, String key, String status) {
        jdbc.update("insert into chenile_trigger_execution values (?, ?, 'Event', '2026-10-14T00:00:00Z', 'alpha', 'CRON', 'daily', '2026-10-14T00:00:01Z', '2026-10-14T00:00:02Z', ?, 'legacy error', '{\"value\":1}')", id, key, status);
    }

    @Test void preservesClaimsAndMetadataImportsOrphansAndReconcilesStatuses() throws Exception {
        legacyTables();
        claim("claim-id", "complete", "COMPLETED"); execution("history-id", "complete", "COMPLETED");
        claim("missing", "missing", "RECEIVED");
        claim("conflict", "conflict", "FAILED"); execution("conflict", "conflict", "COMPLETED");
        claim("running", "running", "RECEIVED"); execution("running", "running", "RUNNING");
        execution("orphan", "orphan", "COMPLETED");
        migrate(); migrate();
        Map<String, Map<String, Object>> rows = jdbc.queryForList("select * from trigger_log").stream()
                .collect(Collectors.toMap(row -> (String) row.get("trigger_id"), row -> row));
        assertEquals(5, rows.size());
        assertEquals("claim-id", rows.get("complete").get("id"));
        assertEquals("{\"value\":1}", rows.get("complete").get("payload"));
        assertEquals("alpha", rows.get("complete").get("tenant"));
        var context = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree((String) rows.get("complete").get("trigger_context"));
        assertEquals("daily", context.get("x-chenile-crontab-id").asText());
        assertEquals("CRON", context.get("x-chenile-trigger-source").asText());
        assertEquals("RECEIVED", rows.get("missing").get("status"));
        assertNull(rows.get("missing").get("started_at"));
        assertEquals("FAILED", rows.get("conflict").get("status"));
        assertEquals("RUNNING", rows.get("running").get("status"));
        assertEquals("COMPLETED", rows.get("orphan").get("status"));
        assertNull(jdbc.queryForObject("select to_regclass('chenile_trigger_execution')", String.class));
        assertThrows(RuntimeException.class, () -> claim("duplicate", "missing", "RECEIVED"));
    }

    @Test void rejectsAmbiguousExecutionKeysWithoutChangingEitherLegacyTable() {
        legacyTables(); claim("claim", "key", "RECEIVED");
        execution("one", "key", "COMPLETED"); execution("two", "key", "COMPLETED");
        assertThrows(RuntimeException.class, this::migrate);
        assertEquals(2, jdbc.queryForObject("select count(*) from chenile_trigger_execution", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=current_schema() and table_name='trigger_log' and column_name='tenant'", Integer.class));
    }

    @Test void rejectsIdCollisionsWithoutDroppingExecutionHistory() {
        legacyTables(); claim("shared", "first", "COMPLETED"); execution("shared", "other", "COMPLETED");
        assertThrows(RuntimeException.class, this::migrate);
        assertEquals(1, jdbc.queryForObject("select count(*) from chenile_trigger_execution", Integer.class));
        assertEquals("first", jdbc.queryForObject("select trigger_id from trigger_log where id='shared'", String.class));
    }

    @Test void createsFreshSchemaAndCanBeReapplied() throws Exception {
        migrate(); migrate();
        assertEquals(0, jdbc.queryForObject("select count(*) from trigger_log", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=current_schema() and table_name='trigger_log' and column_name='crontab_id'", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=current_schema() and table_name='trigger_log' and column_name='trigger_context'", Integer.class));
        assertNull(jdbc.queryForObject("select to_regclass('chenile_trigger_execution')", String.class));
    }
}
