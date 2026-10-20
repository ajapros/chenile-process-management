package org.chenile.orchestrator.process.outbox;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.chenile.core.context.ContextContainer;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Generic registry dispatch, acknowledgement, retries and per-command dead handling. */
class OutboxDispatcherTest {
  ProcessOutboxRepository repository;
  TransactionTemplate transaction;
  JdbcTemplate jdbc;

  @BeforeEach
  void setUp() throws Exception {
    JdbcDataSource data = new JdbcDataSource();
    data.setURL("jdbc:h2:mem:dispatch_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    try (var connection = data.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("chenile-process-outbox-schema.sql"));
    }
    var manager = new DataSourceTransactionManager(data);
    transaction = new TransactionTemplate(manager);
    jdbc = new JdbcTemplate(data);
    repository = new ProcessOutboxRepository(jdbc, manager);
  }

  @Test
  void failingCommandRetriesThenDies() {
    enqueue(OutboxTestSupport.emitCompleted("p1", "{}"));
    List<OutboxCommand> handled = new ArrayList<>();
    List<String> dead = new ArrayList<>();
    var dispatcher =
        dispatcher(
            c -> {
              handled.add(c);
              throw new IllegalStateException("transient");
            },
            c -> dead.add(c.id));
    assertTrue(dispatcher.processOne());
    assertEquals(1L, repository.backlogCount());
    assertTrue(dead.isEmpty());
    assertTrue(dispatcher.processOne());
    assertEquals(0L, repository.backlogCount());
    assertEquals(List.of(handled.get(1).id), dead);
    assertFalse(dispatcher.processOne());
    assertEquals(1, dead.size());
    assertEquals(2, handled.size());
    assertEquals(2, handled.get(1).attempt);
    assertEquals(1L, repository.deadCount());
  }

  @Test
  void drainAllAcknowledgesEveryDueCommand() {
    enqueue(OutboxTestSupport.startWorker("p1", "SPLITTER", "{}"));
    enqueue(OutboxTestSupport.createSubProcess("p1", "c1", "{}"));
    enqueue(OutboxTestSupport.emitCompleted("p1", "{}"));
    List<OutboxCommand> handled = new ArrayList<>();
    assertEquals(3, dispatcher(handled::add, ignored -> {}).drainAll(100));
    assertEquals(0L, repository.backlogCount());
    assertEquals(3, handled.size());
    assertEquals(
        Set.of("START_WORKER", "CREATE_SUBPROCESS", "EMIT_COMPLETED"),
        handled.stream().map(c -> c.commandType).collect(java.util.stream.Collectors.toSet()));
    assertEquals(
        3L,
        jdbc.queryForObject(
            "select count(*) from chenile_process_outbox where status='DONE'", Long.class));
  }

  @Test
  void anAdditionalCommandOwnsEnqueueDispatchAndDeadHandlingWithoutSchemaChanges()
      throws Exception {
    List<String> calls = new ArrayList<>();
    OutboxCommandLifecycle<String> custom =
        new OutboxCommandLifecycle<>() {
          public String type() {
            return "APPLICATION_CUSTOM_COMMAND";
          }

          public void enqueue(String process) {
            calls.add("enqueue");
            repository.enqueue(
                OutboxCommand.create(process, type(), "custom-receipt", "{\"customArgument\":7}"));
          }

          public void dispatch(OutboxCommand entry) {
            assertEquals("{\"customArgument\":7}", entry.payload);
            calls.add("dispatch");
            throw new IllegalStateException("custom failure");
          }

          public void onDead(OutboxCommand entry) {
            calls.add("dead:" + entry.idempotencyKey);
          }
        };
    var registry = new OutboxCommandRegistry(List.of(custom));
    transaction.executeWithoutResult(
        tx -> {
          try {
            new OutboxCommandProducer<>(List.of(custom)).enqueue("custom-process");
          } catch (Exception failure) {
            throw new IllegalStateException(failure);
          }
        });
    OutboxRetryPolicy policy = OutboxRetryPolicy.defaults();
    policy.maxAttempts = 1;
    assertTrue(
        new OutboxDispatcher(repository, registry, policy, "custom-worker", 300).processOne());
    assertEquals(List.of("enqueue", "dispatch", "dead:custom-receipt"), calls);
    assertEquals(1L, repository.deadCount());
  }

  @Test
  void commandContextSurvivesTransactionCompletionAndCallerContextIsRestored() {
    var context = ContextContainer.getInstance();
    var original = context.snapshot();
    try {
      for (boolean fail : List.of(false, true)) {
        context.clear();
        context.setTenant("caller");
        context.put("x-caller-only", "private");
        ContextContainer.putExtension("caller-extension", "preserved");
        var command = OutboxTestSupport.emitCompleted("context-" + fail, "{}");
        command.tenantId = "command-tenant";
        enqueue(command);
        var claimed = repository.claimNext("worker", 300, 2).orElseThrow();
        List<String> callbacks = new ArrayList<>();
        OutboxCommandLifecycle<String> handler =
            new OutboxCommandLifecycle<>() {
              public String type() {
                return "EMIT_COMPLETED";
              }

              public void enqueue(String ignored) {}

              public Map<String, String> executionHeaders(OutboxCommand entry) {
                return Map.of("x-chenile-tenant-id", "spoofed", "x-user-id", "submitter");
              }

              public void dispatch(OutboxCommand entry) {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                assertEquals("command-tenant", context.getTenant());
                assertEquals("submitter", context.get("x-user-id"));
                assertNull(context.get("x-caller-only"));
                TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                      public void beforeCommit(boolean readOnly) {
                        callbacks.add(
                            "commit:" + context.getTenant() + ":" + context.get("x-user-id"));
                      }

                      public void afterCompletion(int status) {
                        callbacks.add(
                            "complete:"
                                + status
                                + ":"
                                + context.getTenant()
                                + ":"
                                + context.get("x-user-id"));
                      }
                    });
                // Simulate Chenile clearing subscriber context on its return journey.
                context.clear();
                if (fail) throw new IllegalStateException("rollback");
              }
            };
        if (fail)
          assertThrows(
              IllegalStateException.class, () -> repository.handleAndComplete(claimed, handler));
        else repository.handleAndComplete(claimed, handler);
        assertEquals(
            fail
                ? List.of("complete:1:command-tenant:submitter")
                : List.of("commit:command-tenant:submitter", "complete:0:command-tenant:submitter"),
            callbacks);
        assertEquals("caller", context.getTenant());
        assertEquals("private", context.get("x-caller-only"));
        assertEquals("preserved", ContextContainer.getExtension("caller-extension"));
        assertNull(context.get("x-user-id"));
        assertEquals(
            fail ? "RUNNING" : "DONE", repository.findById(claimed.id).orElseThrow().status);
        if (fail) repository.markDone(claimed);
      }
    } finally {
      context.restore(original);
    }
  }

  private OutboxDispatcher dispatcher(
      Consumer<OutboxCommand> dispatch, Consumer<OutboxCommand> dead) {
    var registry =
        new OutboxCommandRegistry(
            List.of(
                OutboxTestSupport.command("START_WORKER", dispatch, dead),
                OutboxTestSupport.command("CREATE_SUBPROCESS", dispatch, dead),
                OutboxTestSupport.command("EMIT_COMPLETED", dispatch, dead)));
    OutboxRetryPolicy policy = new OutboxRetryPolicy();
    policy.initialIntervalMillis = 0L;
    policy.maxAttempts = 2;
    return new OutboxDispatcher(repository, registry, policy, "test-worker", 300);
  }

  private void enqueue(OutboxCommand command) {
    transaction.executeWithoutResult(tx -> repository.enqueue(command));
  }
}
