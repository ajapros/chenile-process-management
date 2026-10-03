package org.chenile.orchestrator.process.outbox;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real PostgreSQL tests; each test owns an isolated, disposable schema. */
@EnabledIfSystemProperty(named="chenile.outbox.test.jdbc-url", matches=".+")
class PostgresOutboxTest {
    DriverManagerDataSource admin, data;
    JdbcTemplate jdbc;
    TransactionTemplate transaction;
    ProcessOutboxRepository repository;
    String schema;

    @BeforeEach void setUp() throws Exception {
        String url = System.getProperty("chenile.outbox.test.jdbc-url");
        String user = System.getProperty("chenile.outbox.test.username", System.getProperty("user.name"));
        String password = System.getProperty("chenile.outbox.test.password", "");
        admin = new DriverManagerDataSource(url, user, password);
        schema = "outbox_test_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(admin).execute("create schema " + schema);
        data = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema, user, password);
        jdbc = new JdbcTemplate(data);
        try (Connection connection = data.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("chenile-process-outbox-schema.sql"));
        }
        var manager = new DataSourceTransactionManager(data);
        transaction = new TransactionTemplate(manager);
        repository = new ProcessOutboxRepository(jdbc, manager);
    }

    @AfterEach void cleanUp() {
        if (schema != null) new JdbcTemplate(admin).execute("drop schema " + schema + " cascade");
    }

    void enqueue(String process) {
        transaction.executeWithoutResult(tx -> repository.enqueue(OutboxTestSupport.emitCompleted(process, "{}")));
    }

    void expire(String id) {
        jdbc.update("update chenile_process_outbox set locked_until=current_timestamp - interval '1 second' where id=?", id);
    }

    @Test void staleClaimCannotExecuteOrAcknowledgeAfterReclaim() {
        enqueue("p1");
        OutboxCommand old = repository.claimNext("old", 300, 3).orElseThrow();
        expire(old.id);
        OutboxCommand fresh = repository.claimNext("new", 300, 3).orElseThrow();
        assertNotEquals(old.claimToken, fresh.claimToken);
        assertFalse(repository.markDone(old));
        assertFalse(repository.markFailure(old, OutboxRetryPolicy.defaults(), "stale"));
        assertThrows(IllegalStateException.class, () -> repository.handleAndComplete(old, OutboxTestSupport.command(old.commandType, c -> fail("stale handler ran"))));
        assertTrue(repository.markDone(fresh));
    }

    @Test void finalAttemptCrashBecomesDeadAndCanBeReplayed() {
        enqueue("p1");
        OutboxCommand crashed = repository.claimNext("crashed", 300, 1).orElseThrow();
        expire(crashed.id);
        assertTrue(repository.claimNext("next", 300, 1).isEmpty());
        assertEquals(1, repository.deadCount());
        assertTrue(repository.replayDead(crashed.id));
        OutboxCommand retry = repository.claimNext("replay", 300, 1).orElseThrow();
        assertEquals(1, retry.attempt);
        assertFalse(repository.markDone(crashed));
        assertTrue(repository.markDone(retry));
    }

    @Test void lockedRowIsSkippedByAnotherConnection() throws Exception {
        enqueue("p1"); enqueue("p2");
        var pool = Executors.newSingleThreadExecutor();
        try (Connection locked = data.getConnection()) {
            locked.setAutoCommit(false);
            String id;
            try (var rows = locked.createStatement().executeQuery("select id from chenile_process_outbox order by visible_after,created_at,id limit 1 for update")) {
                assertTrue(rows.next()); id = rows.getString(1);
            }
            OutboxCommand other = pool.submit(() -> repository.claimNext("parallel", 300, 3).orElseThrow()).get(5, TimeUnit.SECONDS);
            assertNotEquals(id, other.id);
            locked.rollback();
            assertEquals(id, repository.claimNext("unlocked", 300, 3).orElseThrow().id);
        } finally { pool.shutdownNow(); }
    }

    @Test void duplicateInsertDoesNotAbortPostgresTransaction() {
        transaction.executeWithoutResult(tx -> {
            assertTrue(repository.enqueue(OutboxTestSupport.emitCompleted("p1", "{}")));
            assertFalse(repository.enqueue(OutboxTestSupport.emitCompleted("p1", "{}")));
            assertTrue(repository.receive("CHAIN", "receipt"));
            assertFalse(repository.receive("CHAIN", "receipt"));
            assertTrue(repository.enqueue(OutboxTestSupport.emitCompleted("p2", "{}")));
        });
        assertEquals(2, repository.backlogCount());
    }

    @Test void receiptAndBusinessMutationRollBackWithFailedHandler() {
        jdbc.execute("create table business_effect (id varchar(64) primary key)");
        enqueue("p1");
        OutboxCommand command = repository.claimNext("worker", 300, 3).orElseThrow();
        assertThrows(IllegalStateException.class, () -> repository.handleAndComplete(command, OutboxTestSupport.command(command.commandType, c -> {
            assertTrue(repository.receive("test", c.idempotencyKey));
            jdbc.update("insert into business_effect values ('p1')");
            throw new IllegalStateException("crash before ack");
        })));
        assertEquals(0L, jdbc.queryForObject("select count(*) from business_effect", Long.class));
        assertEquals(0L, jdbc.queryForObject("select count(*) from chenile_process_receipt", Long.class));
        repository.handleAndComplete(command, OutboxTestSupport.command(command.commandType, c -> {
            assertTrue(repository.receive("test", c.idempotencyKey));
            jdbc.update("insert into business_effect values ('p1')");
        }));
        assertEquals(1L, jdbc.queryForObject("select count(*) from business_effect", Long.class));
        assertEquals("DONE", jdbc.queryForObject("select status from chenile_process_outbox where id=?", String.class, command.id));
    }

    @Test void legacyParentSignalsMigrateToJsonWithoutLosingDeliveryOrReceiptIdentity() throws Exception {
        jdbc.execute("alter table chenile_process_outbox add column target_id varchar(128)");
        jdbc.execute("alter table chenile_process_outbox add column event_name varchar(64)");
        jdbc.execute("alter table chenile_process_outbox add column worker_type varchar(32)");
        transaction.executeWithoutResult(tx -> repository.enqueue(OutboxCommand.create("child", "SIGNAL_PARENT",
                "signal:parent:child:subProcessDoneSuccessfully", "{\"childId\":\"child\"}")));
        jdbc.update("update chenile_process_outbox set target_id='parent', event_name='subProcessDoneSuccessfully'");
        String migration = new ClassPathResource("chenile-process-outbox-json-migration.sql")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        jdbc.execute(migration);
        jdbc.execute(migration);
        OutboxCommand migrated = repository.claimNext("after-upgrade", 300, 3).orElseThrow();
        assertEquals("signal:parent:child:subProcessDoneSuccessfully", migrated.idempotencyKey);
        assertEquals("parent", jdbc.queryForObject("select payload::jsonb->>'parentId' from chenile_process_outbox", String.class));
        assertEquals("child", jdbc.queryForObject("select payload::jsonb->>'childId' from chenile_process_outbox", String.class));
        assertEquals("child", jdbc.queryForObject("select payload::jsonb->'payload'->>'childId' from chenile_process_outbox", String.class));
        assertEquals(0L, jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=? " +
                "and table_name='chenile_process_outbox' and column_name in ('target_id','event_name','worker_type')", Long.class, schema));
    }
}
