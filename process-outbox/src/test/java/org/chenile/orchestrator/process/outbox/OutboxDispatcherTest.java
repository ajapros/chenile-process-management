package org.chenile.orchestrator.process.outbox;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

/** Generic registry dispatch, acknowledgement, retries and per-command dead handling. */
class OutboxDispatcherTest {
    ProcessOutboxRepository repository;
    TransactionTemplate transaction;
    JdbcTemplate jdbc;

    @BeforeEach void setUp() throws Exception {
        JdbcDataSource data = new JdbcDataSource();
        data.setURL("jdbc:h2:mem:dispatch_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (var connection = data.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("chenile-process-outbox-schema.sql"));
        }
        var manager = new DataSourceTransactionManager(data);
        transaction = new TransactionTemplate(manager);
        jdbc = new JdbcTemplate(data);
        repository = new ProcessOutboxRepository(jdbc, manager);
    }

    @Test void failingCommandRetriesThenDies() {
        enqueue(OutboxTestSupport.emitCompleted("p1", "{}"));
        List<OutboxCommand> handled = new ArrayList<>();
        var dispatcher = dispatcher(c -> { handled.add(c); throw new IllegalStateException("transient"); }, ignored -> { });
        assertTrue(dispatcher.processOne());
        assertEquals(1L, repository.backlogCount());
        assertTrue(dispatcher.processOne());
        assertEquals(0L, repository.backlogCount());
        assertFalse(dispatcher.processOne());
        assertEquals(2, handled.size());
        assertEquals(2, handled.get(1).attempt);
        assertEquals(1L, repository.deadCount());
    }

    @Test void drainAllAcknowledgesEveryDueCommand() {
        enqueue(OutboxTestSupport.startWorker("p1", "SPLITTER", "{}"));
        enqueue(OutboxTestSupport.createSubProcess("p1", "c1", "{}"));
        enqueue(OutboxTestSupport.emitCompleted("p1", "{}"));
        List<OutboxCommand> handled = new ArrayList<>();
        assertEquals(3, dispatcher(handled::add, ignored -> { }).drainAll(100));
        assertEquals(0L, repository.backlogCount());
        assertEquals(3, handled.size());
        assertEquals(Set.of("START_WORKER", "CREATE_SUBPROCESS", "EMIT_COMPLETED"),
                handled.stream().map(c -> c.commandType).collect(java.util.stream.Collectors.toSet()));
        assertEquals(3L, jdbc.queryForObject("select count(*) from chenile_process_outbox where status='DONE'", Long.class));
    }

    @Test void exhaustionInvokesTheMatchingCommandsDeadMethodExactlyOnce() {
        enqueue(OutboxTestSupport.startWorker("p1", "EXECUTOR", "{}"));
        List<String> dead = new ArrayList<>();
        var dispatcher = dispatcher(c -> { throw new IllegalStateException("always fails"); }, c -> dead.add(c.id));
        assertTrue(dispatcher.processOne());
        assertTrue(dead.isEmpty());
        assertTrue(dispatcher.processOne());
        assertEquals(1, dead.size());
        assertFalse(dispatcher.processOne());
        assertEquals(1, dead.size());
    }

    @Test void anAdditionalCommandOwnsEnqueueDispatchAndDeadHandlingWithoutSchemaChanges() throws Exception {
        List<String> calls = new ArrayList<>();
        OutboxCommandLifecycle<String> custom = new OutboxCommandLifecycle<>() {
            public String type() { return "APPLICATION_CUSTOM_COMMAND"; }
            public void enqueue(String process) {
                calls.add("enqueue");
                repository.enqueue(OutboxCommand.create(process, type(), "custom-receipt", "{\"customArgument\":7}"));
            }
            public void dispatch(OutboxCommand entry) {
                assertEquals("{\"customArgument\":7}", entry.payload);
                calls.add("dispatch");
                throw new IllegalStateException("custom failure");
            }
            public void onDead(OutboxCommand entry) { calls.add("dead:" + entry.idempotencyKey); }
        };
        var registry = new OutboxCommandRegistry<>(List.of(custom));
        transaction.executeWithoutResult(tx -> {
            try { registry.enqueue("custom-process"); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        });
        OutboxRetryPolicy policy = OutboxRetryPolicy.defaults(); policy.maxAttempts = 1;
        assertTrue(new OutboxDispatcher(repository, registry, policy, "custom-worker", 300).processOne());
        assertEquals(List.of("enqueue", "dispatch", "dead:custom-receipt"), calls);
        assertEquals(1L, repository.deadCount());
    }

    private OutboxDispatcher dispatcher(Consumer<OutboxCommand> dispatch, Consumer<OutboxCommand> dead) {
        var registry = new OutboxCommandRegistry<>(List.of(
                OutboxTestSupport.command("START_WORKER", dispatch, dead),
                OutboxTestSupport.command("CREATE_SUBPROCESS", dispatch, dead),
                OutboxTestSupport.command("EMIT_COMPLETED", dispatch, dead)));
        OutboxRetryPolicy policy = new OutboxRetryPolicy();
        policy.initialIntervalMillis = 0L; policy.maxAttempts = 2;
        return new OutboxDispatcher(repository, registry, policy, "test-worker", 300);
    }

    private void enqueue(OutboxCommand command) {
        transaction.executeWithoutResult(tx -> repository.enqueue(command));
    }
}
