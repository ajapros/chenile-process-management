package org.chenile.orchestrator.process.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.chenile.orchestrator.process.SpringTestConfig;
import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.api.ProcessManager;
import org.chenile.orchestrator.process.config.reader.ProcessConfigurator;
import org.chenile.orchestrator.process.configuration.dao.ProcessRepository;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.ProcessCompletedEvent;
import org.chenile.orchestrator.process.model.ProcessDto;
import org.chenile.orchestrator.process.model.payload.DoneSuccessfullyPayload;
import org.chenile.orchestrator.process.model.payload.DoneWithErrorsPayload;
import org.chenile.orchestrator.process.model.payload.StartProcessingPayload;
import org.chenile.orchestrator.process.model.payload.SubProcessPayload;
import org.chenile.orchestrator.process.outbox.*;
import org.chenile.orchestrator.process.service.outbox.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    classes = SpringTestConfig.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:durable-process;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "chenile.process.outbox.run-dispatcher=false",
      "spring.sql.init.mode=always",
      "spring.sql.init.schema-locations=classpath:chenile-process-outbox-schema.sql"
    })
@ActiveProfiles("unittest")
class DurableProcessIntegrationTest {
  @Autowired ProcessManager manager;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  org.chenile.core.event.EventProcessor events;

  @Autowired ProcessConfigurator configurator;
  @Autowired ProcessRepository processes;
  @Autowired ProcessOutboxRepository outbox;
  @Autowired OutboxDispatcher dispatcher;
  @Autowired OutboxCommandRegistry commands;
  @MockitoBean WorkerStarter workers;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactionManager;
  final List<String> dispatches = new CopyOnWriteArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            tx -> {
              processes.deleteAll();
              jdbc.update("delete from chenile_process_outbox");
              jdbc.update("delete from chenile_process_receipt");
            });
    dispatches.clear();
    doAnswer(
            call -> {
              dispatches.add(
                  ((org.chenile.orchestrator.process.model.WorkerDto) call.getArgument(0))
                      .dispatchId);
              return null;
            })
        .when(workers)
        .start(any());
    configurator.read(
        new ByteArrayInputStream(
            """
{"processMap":{"parent":{"leaf":false},"child":{"leaf":true},"source":{"leaf":true},"successor":{"leaf":true,
 "predecessorProcessType":"source","predecessorArgs":"OUTPUT"}}}
"""
                .getBytes(StandardCharsets.UTF_8)));
  }

  private String create() {
    ProcessDto dto = new ProcessDto();
    dto.processDefName = "source";
    return manager.create(dto).getMutatedEntity().getId();
  }

  @Test
  void creationAndOutboxRollBackTogether() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                    tx -> {
                      create();
                      assertEquals(1L, outbox.backlogCount());
                      throw new IllegalStateException("crash before commit");
                    }));
    assertEquals(0L, processes.count());
    assertEquals(0L, outbox.backlogCount());
    assertTrue(dispatches.isEmpty());
  }

  @Test
  void committedCreationCanBeDispatchedLater() {
    String id =
        new TransactionTemplate(transactionManager)
            .execute(
                tx -> {
                  String created = create();
                  assertEquals(1L, outbox.backlogCount());
                  // A separate dispatcher cannot observe this transition's uncommitted command.
                  assertFalse(
                      java.util.concurrent.CompletableFuture.supplyAsync(dispatcher::processOne)
                          .join());
                  assertTrue(dispatches.isEmpty());
                  return created;
                });
    dispatcher.drainAll(20);
    assertEquals(List.of(id + ":EXECUTOR"), dispatches);
    assertEquals(0L, outbox.backlogCount());
    assertEquals(0L, outbox.deadCount());
  }

  @Test
  void failedCompletionSubscriberRollsBackSuccessorsAndRetriesWithoutUndoingTheTransition()
      throws Exception {
    String id = create();
    dispatcher.drainAll(20);
    manager.processById(id, Constants.Events.DONE_SUCCESSFULLY, new DoneSuccessfullyPayload());
    assertTrue(processes.findByPredecessorId(id).isEmpty());
    doAnswer(
            call -> {
              @SuppressWarnings("unchecked")
              var replies =
                  new java.util.ArrayList<>(
                      (java.util.List<org.chenile.core.context.ChenileExchange>)
                          call.callRealMethod());
              var failed = new org.chenile.core.context.ChenileExchange();
              failed.setException(new IllegalStateException("application subscriber unavailable"));
              replies.add(failed);
              return replies;
            })
        .when(events)
        .handleEvent(eq(Constants.Events.PROCESS_COMPLETED), any(), anyMap());
    assertTrue(dispatcher.processOne());
    assertEquals(
        Constants.States.PROCESSED,
        processes.findById(id).orElseThrow().getCurrentState().getStateId());
    assertTrue(processes.findByPredecessorId(id).isEmpty());
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from chenile_process_receipt where consumer='CHAIN'", Integer.class));
    assertEquals(
        "PENDING",
        jdbc.queryForObject(
            "select status from chenile_process_outbox where process_id=? and"
                + " command_type='EMIT_COMPLETED'",
            String.class,
            id));
    doCallRealMethod()
        .when(events)
        .handleEvent(eq(Constants.Events.PROCESS_COMPLETED), any(), anyMap());
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            tx ->
                jdbc.update(
                    "update chenile_process_outbox set visible_after=current_timestamp where"
                        + " status='PENDING'"));
    dispatcher.drainAll(20);
    assertEquals(1, processes.findByPredecessorId(id).size());
    assertEquals(0L, outbox.backlogCount());
    assertEquals(0L, outbox.deadCount());
  }

  @Test
  void deadWorkerUsesItsJsonPayloadToCompensateTheProcess() {
    doThrow(new IllegalStateException("dispatch unavailable")).when(workers).start(any());
    String id = create();
    OutboxRetryPolicy policy = OutboxRetryPolicy.defaults();
    policy.maxAttempts = 1;
    var failingDispatcher = new OutboxDispatcher(outbox, commands, policy, "dead-worker-test", 300);
    assertTrue(failingDispatcher.processOne());
    var process = processes.findById(id).orElseThrow();
    assertEquals(Constants.States.PROCESSED_WITH_ERRORS, process.getCurrentState().getStateId());
    assertFalse(process.errors.isEmpty());
    assertEquals(1L, outbox.deadCount());
    doAnswer(
            call -> {
              dispatches.add(
                  ((org.chenile.orchestrator.process.model.WorkerDto) call.getArgument(0))
                      .dispatchId);
              return null;
            })
        .when(workers)
        .start(any());
    dispatcher.drainAll(20);
    assertEquals(1, processes.findByPredecessorId(id).size());
    assertEquals(0L, outbox.backlogCount());
  }

  @Test
  void enqueueFailureRollsBackTheProcessSave() {
    assertThrows(
        RuntimeException.class,
        () ->
            new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                    tx -> {
                      create();
                      outbox.enqueue(
                          OutboxCommand.create(null, EmitCompletedCommand.TYPE, "invalid", null));
                    }));
    assertEquals(0L, processes.count());
    assertEquals(0L, outbox.backlogCount());
  }

  @Test
  void childCreationAndParentCountingAreReplaySafe() throws Exception {
    ProcessDto dto = new ProcessDto();
    dto.processDefName = "parent";
    String parentId = manager.create(dto).getMutatedEntity().getId();
    dispatcher.drainAll(20);
    StartProcessingPayload split = new StartProcessingPayload();
    SubProcessPayload one = new SubProcessPayload();
    one.processType = "child";
    one.leaf = true;
    one.args = "{}";
    SubProcessPayload two = new SubProcessPayload();
    two.processType = "child";
    two.leaf = true;
    two.args = "{}";
    split.subProcesses = List.of(one, two);
    manager.processById(parentId, Constants.Events.SPLIT_DONE, split);
    assertEquals(1L, processes.count(), "child creation is deferred until the state commit");
    dispatcher.drainAll(20);
    var children = processes.findByParentId(parentId);
    assertEquals(2, children.size());
    String childId = children.get(0).getId();
    manager.processById(childId, Constants.Events.DONE_SUCCESSFULLY, new DoneSuccessfullyPayload());
    dispatcher.drainAll(20);
    assertEquals(1, processes.findById(parentId).orElseThrow().numCompletedSubProcesses);
    DoneSuccessfullyPayload signal = new DoneSuccessfullyPayload();
    signal.childId = childId;
    ObjectMapper mapper = new ObjectMapper();
    OutboxCommand duplicate =
        OutboxCommand.create(
            childId,
            SignalParentCommand.TYPE,
            "duplicate-parent-signal",
            mapper.writeValueAsString(
                new SignalParentCommand.Data(
                    parentId,
                    childId,
                    Constants.Events.SUB_PROCESS_DONE_SUCCESSFULLY,
                    mapper.valueToTree(signal))));
    duplicate.idempotencyKey += ":duplicate-test";
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(tx -> outbox.enqueue(duplicate));
    dispatcher.drainAll(20);
    assertEquals(1, processes.findById(parentId).orElseThrow().numCompletedSubProcesses);
    manager.processById(
        children.get(1).getId(), Constants.Events.DONE_SUCCESSFULLY, new DoneSuccessfullyPayload());
    dispatcher.drainAll(20);
    assertEquals(2, processes.findById(parentId).orElseThrow().numCompletedSubProcesses);
    assertEquals(
        Constants.States.AGGREGATION_PENDING,
        processes.findById(parentId).orElseThrow().getCurrentState().getStateId());
    assertEquals(0L, outbox.deadCount());
  }

  @Test
  void completionAndReplayedCommandCreateExactlyOneSuccessor() throws Exception {
    String id = create();
    dispatcher.drainAll(20);
    DoneSuccessfullyPayload done = new DoneSuccessfullyPayload();
    done.output = "{\"rows\":5}";
    manager.processById(id, Constants.Events.DONE_SUCCESSFULLY, done);
    assertTrue(
        processes.findByPredecessorId(id).isEmpty(), "chaining must wait for the committed outbox");
    dispatcher.drainAll(20);
    var successors = processes.findByPredecessorId(id);
    assertEquals(1, successors.size());
    assertEquals("{\"output\":{\"rows\":5}}", successors.get(0).input);
    ProcessCompletedEvent event = new ProcessCompletedEvent();
    event.processId = id;
    event.processType = "source";
    event.output = done.output;
    OutboxCommand replay =
        OutboxCommand.create(
            id,
            EmitCompletedCommand.TYPE,
            id + ":completed",
            new ObjectMapper().writeValueAsString(event));
    replay.idempotencyKey += ":replay-test";
    new TransactionTemplate(transactionManager).executeWithoutResult(tx -> outbox.enqueue(replay));
    dispatcher.drainAll(20);
    assertEquals(1, processes.findByPredecessorId(id).size());
    assertEquals(2, dispatches.size());
    assertEquals(0L, outbox.deadCount());
  }

  @Test
  void concurrentSuccessAndErrorSignalsCountBothChildrenOnce() throws Exception {
    ProcessDto dto = new ProcessDto();
    dto.processDefName = "parent";
    String parentId = manager.create(dto).getMutatedEntity().getId();
    dispatcher.drainAll(20);
    StartProcessingPayload split = new StartProcessingPayload();
    SubProcessPayload one = new SubProcessPayload();
    one.processType = "child";
    one.leaf = true;
    SubProcessPayload two = new SubProcessPayload();
    two.processType = "child";
    two.leaf = true;
    split.subProcesses = List.of(one, two);
    manager.processById(parentId, Constants.Events.SPLIT_DONE, split);
    dispatcher.drainAll(20);
    var children = processes.findByParentId(parentId);
    manager.processById(
        children.get(0).getId(), Constants.Events.DONE_SUCCESSFULLY, new DoneSuccessfullyPayload());
    DoneWithErrorsPayload error = new DoneWithErrorsPayload();
    error.exceptionMessage = "failed child";
    manager.processById(children.get(1).getId(), Constants.Events.DONE_WITH_ERRORS, error);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var first = pool.submit(() -> dispatcher.drainAll(30));
      var second = pool.submit(() -> dispatcher.drainAll(30));
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }
    dispatcher.drainAll(30);
    var parent = processes.findById(parentId).orElseThrow();
    assertEquals(2, parent.numCompletedSubProcesses);
    assertEquals(Constants.States.AGGREGATION_PENDING, parent.getCurrentState().getStateId());
    assertFalse(parent.errors.isEmpty());
    assertEquals(0L, outbox.backlogCount());
    assertEquals(0L, outbox.deadCount());
  }
}
