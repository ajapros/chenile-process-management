package org.chenile.orchestrator.process.configuration.management;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProcessHistorySchemaTest {
    @Test void freshSchemaOmitsCompletionSnapshotsAndCleanupPreservesProcessAndTriggerData() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:history-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        // H2 lacks PostgreSQL's timestamptz alias; keep the shipped PostgreSQL script unchanged.
        jdbc.execute("create domain timestamptz as timestamp with time zone");
        jdbc.execute("create table process_table (id varchar primary key, tenant varchar, created_time timestamp, trigger_id varchar, parent_id varchar, predecessor_id varchar, status varchar)");
        jdbc.update("insert into process_table (id, status) values ('process-1', 'PROCESSED')");
        var schema = new ResourceDatabasePopulator(new ClassPathResource("chenile-process-management-history-schema.sql"));
        schema.execute(source);
        schema.execute(source);
        assertEquals(0, jdbc.queryForObject("select count(*) from information_schema.tables where table_name='CHENILE_PROCESS_COMPLETION_EVENT'", Integer.class));
        jdbc.update("insert into chenile_trigger_execution (id, trigger_id, event_name, trigger_time, started_at) values ('trigger-1', 'key-1', 'ProcessCreate', current_timestamp, current_timestamp)");
        jdbc.execute("create table chenile_process_completion_event (process_id varchar primary key, payload clob)");
        jdbc.update("insert into chenile_process_completion_event values ('process-1', '{}')");

        var cleanup = new ResourceDatabasePopulator(new ClassPathResource("chenile-process-completion-event-removal.sql"));
        cleanup.execute(source);
        cleanup.execute(source);

        assertEquals(0, jdbc.queryForObject("select count(*) from information_schema.tables where table_name='CHENILE_PROCESS_COMPLETION_EVENT'", Integer.class));
        assertEquals("PROCESSED", jdbc.queryForObject("select status from process_table where id='process-1'", String.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from chenile_trigger_execution", Integer.class));
    }
}
