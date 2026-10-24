package org.chenile.orchestrator.process.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import org.chenile.core.context.ChenileExchange;
import org.chenile.core.context.ContextContainer;
import org.chenile.base.exception.ServerException;
import org.chenile.orchestrator.delegate.ProcessManagerClient;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.AggregationDonePayload;
import org.chenile.orchestrator.process.model.payload.DoneSuccessfullyPayload;
import org.chenile.orchestrator.process.model.payload.DoneWithErrorsPayload;
import org.chenile.orchestrator.process.model.payload.StartProcessingPayload;
import org.chenile.orchestrator.process.model.payload.SubProcessPayload;
import org.chenile.orchestrator.process.workers.WorkListenerInput;
import org.chenile.orchestrator.process.workers.WorkListenerService;
import org.chenile.orchestrator.process.workers.WorkerConstants;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.core.type.TypeReference;

class ProcessWorkListenerTest {
  @Test
  void selectsTheConcreteListenerUsingProcessAndWorkerHeaders() {
    ChenileExchange exchange = new ChenileExchange();
    exchange.setHeader(WorkerConstants.PROCESS_TYPE, "feed");
    exchange.setHeader(WorkerConstants.WORKER_TYPE, "EXECUTOR");

    String selected = new ProcessWorkServiceSelector().selectServiceReferenceName(exchange, null);

    assertEquals("feedExecutor", selected);
  }

  @Test
  void concreteListenerUsesTheWorkListenerServiceContractWithoutComponentAnnotation() {
    assertFalse(TestExecutor.class.isAnnotationPresent(org.springframework.stereotype.Component.class));
    assertTrue(WorkListenerService.class.isAssignableFrom(TestExecutor.class));
  }

  @Test
  void bodySelectorResolvesTheConcreteListenerPayloadType() {
    ApplicationContext applicationContext = mock(ApplicationContext.class);
    TestExecutor listener = new TestExecutor(mock(ProcessManagerClient.class));
    when(applicationContext.getBean("feedExecutor")).thenReturn(listener);
    ProcessWorkBodyTypeSelector selector = new ProcessWorkBodyTypeSelector();
    ReflectionTestUtils.setField(selector, "applicationContext", applicationContext);
    ChenileExchange exchange = new ChenileExchange();
    exchange.setHeader(WorkerConstants.PROCESS_TYPE, "feed");
    exchange.setHeader(WorkerConstants.WORKER_TYPE, "executor");

    selector.execute(exchange);

    ParameterizedType inputType = (ParameterizedType) exchange.getBodyType().getType();
    assertEquals(WorkListenerInput.class, inputType.getRawType());
    assertEquals(Payload.class, inputType.getActualTypeArguments()[0]);
  }

  @Test
  void executorReportsItsTypedOutputAndRestoresTenantContext() {
    ProcessManagerClient processManagerClient = mock(ProcessManagerClient.class);
    TestExecutor listener = new TestExecutor(processManagerClient);
    WorkListenerInput<Payload> input = input(new Payload("value"));

    boolean accepted = withTenant(() -> listener.accept("feed", "EXECUTOR", input));

    assertTrue(accepted);
    verify(processManagerClient).doneSuccessfully(eq("process-1"), any(DoneSuccessfullyPayload.class));
    assertEquals("", ContextContainer.CONTEXT_CONTAINER.getTenant());
  }

  @Test
  void sharedBaseReportsWorkerFailuresAndAllowsQueueRetryWhenCallbackFails() {
    ProcessManagerClient processManagerClient = mock(ProcessManagerClient.class);
    FailingExecutor listener = new FailingExecutor(processManagerClient);
    WorkListenerInput<Payload> input = input(new Payload("value"));

    boolean accepted = withTenant(() -> listener.accept("feed", "EXECUTOR", input));

    assertTrue(accepted);
    verify(processManagerClient)
        .process(eq("process-1"), eq("doneWithErrors"), any(DoneWithErrorsPayload.class));

    org.mockito.Mockito.when(
            processManagerClient.process(
                eq("process-1"), eq("doneWithErrors"), any(DoneWithErrorsPayload.class)))
        .thenThrow(new IllegalStateException("process manager unavailable"));
    assertThrows(
        ServerException.class,
        () -> withTenant(() -> listener.accept("feed", "EXECUTOR", input)));
  }

  @Test
  void splitterPublishesItsSubprocesses() {
    ProcessManagerClient processManagerClient = mock(ProcessManagerClient.class);
    TestSplitter listener = new TestSplitter(processManagerClient);

    boolean accepted = withTenant(() -> listener.accept("feed", "SPLITTER", input(new Payload("value"))));

    assertTrue(accepted);
    verify(processManagerClient).splitDone(eq("process-1"), any(StartProcessingPayload.class));
  }

  @Test
  void aggregatorFoldsTypedChildOutputsAndReportsTheResult() {
    ProcessManagerClient processManagerClient = mock(ProcessManagerClient.class);
    Process self = process("process-1", null);
    Process child = process("child-1", "{\"value\":\"child-value\"}");
    when(processManagerClient.getSubProcesses("process-1", false)).thenReturn(List.of(self, child));
    TestAggregator listener = new TestAggregator(processManagerClient);

    boolean accepted = withTenant(() -> listener.accept("feed", "AGGREGATOR", input(new Payload("value"))));

    assertTrue(accepted);
    verify(processManagerClient).aggregationDone(eq("process-1"), any(AggregationDonePayload.class));
  }

  private static Process process(String id, String output) {
    Process process = new Process();
    process.setId(id);
    process.output = output;
    return process;
  }

  private static WorkListenerInput<Payload> input(Payload payload) {
    WorkListenerInput<Payload> input = new WorkListenerInput<>();
    input.processId = "process-1";
    input.dispatchId = "dispatch-1";
    input.payload = payload;
    return input;
  }

  private static boolean withTenant(java.util.function.BooleanSupplier invocation) {
    ContextContainer context = ContextContainer.CONTEXT_CONTAINER;
    ContextContainer.ContextSnapshot snapshot = context.snapshot();
    try {
      context.setTenant("tenant-1");
      return invocation.getAsBoolean();
    } finally {
      context.restore(snapshot);
    }
  }

  private static class TestExecutor extends ExecutorWorkListener<Payload, Output> {
    TestExecutor(ProcessManagerClient processManagerClient) {
      this.processManagerClient = processManagerClient;
    }

    @Override
    public Output doStart(WorkListenerInput<Payload> input) {
      Output output = new Output();
      output.value = input.payload.value;
      return output;
    }
  }

  private static class TestSplitter extends SplitterWorkListener<Payload, Payload> {
    TestSplitter(ProcessManagerClient processManagerClient) {
      this.processManagerClient = processManagerClient;
    }

    @Override
    public List<SubProcessPayload> doStart(WorkListenerInput<Payload> input) {
      SubProcessPayload child = new SubProcessPayload();
      child.processType = "child";
      child.args = "{}";
      return List.of(child);
    }
  }

  private static class FailingExecutor extends ExecutorWorkListener<Payload, Output> {
    FailingExecutor(ProcessManagerClient processManagerClient) {
      this.processManagerClient = processManagerClient;
    }

    @Override
    public Output doStart(WorkListenerInput<Payload> input) {
      throw new IllegalStateException("worker failed");
    }
  }

  private static class TestAggregator
      extends AggregatorWorkListener<Payload, List<String>, ChildPayload> {
    TestAggregator(ProcessManagerClient processManagerClient) {
      this.processManagerClient = processManagerClient;
    }

    @Override
    public List<String> doStart(
        List<String> output,
        WorkListenerInput<Payload> input,
        ChildPayload childOutput,
        Process childProcess) {
      List<String> result = output == null ? new java.util.ArrayList<>() : output;
      result.add(childOutput.value);
      return result;
    }
  }

  private static class Payload {
    public String value;

    Payload() {}

    Payload(String value) {
      this.value = value;
    }
  }

  private static class Output {
    public String value;
  }

  private static class ChildPayload {
    public String value;
  }
}
