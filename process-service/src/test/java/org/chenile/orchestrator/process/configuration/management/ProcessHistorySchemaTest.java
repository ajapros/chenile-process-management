package org.chenile.orchestrator.process.configuration.management;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class ProcessHistorySchemaTest {
  @Test
  void freshSchemaOmitsCompletionSnapshotsAndCleanupPreservesProcessAndTriggerData() {
    var source =
        new SingleConnectionDataSource(
            "jdbc:h2:mem:history-" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "", true);
    try {
      var jdbc = new JdbcTemplate(source);
      jdbc.execute(
          "create table process_table (id varchar primary key, tenant varchar, created_time"
              + " timestamp, trigger_id varchar, parent_id varchar, predecessor_id varchar, status"
              + " varchar)");
      jdbc.update("insert into process_table (id, status) values ('process-1', 'PROCESSED')");
      var schema =
          new ResourceDatabasePopulator(
              new ClassPathResource("chenile-process-outbox-schema.sql"),
              new ClassPathResource("chenile-process-management-history-schema.sql"));
      schema.execute(source);
      schema.execute(source);
      assertEquals(
          0,
          jdbc.queryForObject(
              "select count(*) from information_schema.tables where"
                  + " table_name='CHENILE_PROCESS_COMPLETION_EVENT'",
              Integer.class));
      jdbc.update(
          "insert into chenile_process_outbox"
              + " (id,command_type,payload,idempotency_key,status,attempt,visible_after,created_at,updated_at)"
              + " values"
              + " ('trigger-1','DISPATCH_TRIGGER','{}','key-1','DONE',1,current_timestamp,current_timestamp,current_timestamp)");
      jdbc.execute(
          "create table chenile_process_completion_event (process_id varchar primary key, payload"
              + " clob)");
      jdbc.update("insert into chenile_process_completion_event values ('process-1', '{}')");

      var cleanup =
          new ResourceDatabasePopulator(
              new ClassPathResource("chenile-process-completion-event-removal.sql"));
      cleanup.execute(source);
      cleanup.execute(source);

      assertEquals(
          0,
          jdbc.queryForObject(
              "select count(*) from information_schema.tables where"
                  + " table_name='CHENILE_PROCESS_COMPLETION_EVENT'",
              Integer.class));
      assertEquals(
          "PROCESSED",
          jdbc.queryForObject(
              "select status from process_table where id='process-1'", String.class));
      assertEquals(
          1, jdbc.queryForObject("select count(*) from chenile_process_outbox", Integer.class));
    } finally {
      source.destroy();
    }
  }
}
