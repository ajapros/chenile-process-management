package org.chenile.orchestrator.process.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.chenile.base.exception.ErrorNumException;
import org.chenile.base.exception.ServerException;
import org.chenile.base.response.ResponseMessage;
import org.chenile.core.context.ContextContainer;
import org.chenile.core.context.HeaderUtils;
import org.chenile.orchestrator.delegate.ProcessManagerClient;
import org.chenile.orchestrator.process.model.WorkerType;
import org.chenile.orchestrator.process.model.payload.DoneWithErrorsPayload;
import org.chenile.orchestrator.process.utils.ProcessUtil;
import org.chenile.orchestrator.process.workers.WorkListenerInput;
import org.chenile.orchestrator.process.workers.WorkListenerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/** Shared delivery boundary for process-specific work listeners. */
public abstract class AbstractWorkListener<Input> implements WorkListenerService<Input> {
  protected final Logger logger = LoggerFactory.getLogger(getClass());
  @Autowired protected ProcessManagerClient processManagerClient;
  protected final ObjectMapper objectMapper = new ObjectMapper();

  protected AbstractWorkListener() {
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
  }

  @Override
  public final boolean accept(
      String processType, String workerType, WorkListenerInput<Input> input) {
    ContextContainer context = ContextContainer.CONTEXT_CONTAINER;
    ContextContainer.ContextSnapshot snapshot = context.snapshot();
    try {
      validateInput(processType, workerType, input);
      try {
        doWork(input);
        return true;
      } catch (Exception exception) {
        reportFailure(input, processType, workerType, exception);
        return true;
      }
    } finally {
      context.restore(snapshot);
    }
  }

  private void validateInput(
      String processType, String workerType, WorkListenerInput<Input> input) {
    if (input == null || input.processId == null || input.processId.isBlank()) {
      throw new IllegalArgumentException("A process id is required in process work input");
    }
    if (processType == null || processType.isBlank()) {
      throw new IllegalArgumentException("Process type is required");
    }
    WorkerType receivedWorkerType = WorkerListenerNames.parseWorkerType(workerType);
    if (receivedWorkerType != expectedWorkerType()) {
      throw new IllegalArgumentException(
          getClass().getSimpleName() + " received worker type " + receivedWorkerType);
    }
    String tenant = contextTenant();
    if (tenant == null || tenant.isBlank()) {
      throw new IllegalArgumentException("Process work tenant must be present in the Chenile context");
    }
  }

  protected abstract WorkerType expectedWorkerType();

  protected abstract String errorEventId();

  protected abstract void doWork(WorkListenerInput<Input> input) throws Exception;

  private String contextTenant() {
    return ContextContainer.CONTEXT_CONTAINER.get(HeaderUtils.TENANT_ID_KEY);
  }

  private void reportFailure(
      WorkListenerInput<Input> input,
      String processType,
      String workerType,
      Exception failure) {
    logger.error(
        "Worker failure for processId={}, processType={}, workerType={}; reporting event={}",
        input.processId,
        processType,
        workerType,
        errorEventId(),
        failure);
    try {
      DoneWithErrorsPayload payload = errorsPayload(failure);
      processManagerClient.process(input.processId, errorEventId(), payload);
    } catch (Exception reportingFailure) {
      throw new ServerException(
          "WORKER_CALLBACK_FAILED", "Worker callback failed; retry delivery", reportingFailure);
    }
  }

  private DoneWithErrorsPayload errorsPayload(Exception failure) {
    DoneWithErrorsPayload payload = new DoneWithErrorsPayload();
    payload.exceptionMessage = failure.getMessage();
    payload.stackTrace = stackTrace(failure);
    if (failure instanceof ErrorNumException errorNumException) {
      List<String> errors = new ArrayList<>();
      for (ResponseMessage responseMessage : errorNumException.getErrors()) {
        try {
          errors.add(objectMapper.writeValueAsString(responseMessage));
        } catch (Exception serializationFailure) {
          logger.warn(
              "Unable to serialize a structured process error {}",
              responseMessage,
              serializationFailure);
        }
      }
      payload.errors = errors;
    }
    return payload;
  }

  private String stackTrace(Throwable throwable) {
    StringWriter output = new StringWriter();
    throwable.printStackTrace(new PrintWriter(output));
    return output.toString();
  }

  protected void progressUpdate(WorkListenerInput<?> input, int percent) {
    ProcessUtil.processProgressUpdate(processManagerClient, input.processId, percent);
  }
}
