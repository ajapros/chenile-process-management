package org.chenile.orchestrator.process.worker;

import java.util.List;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.WorkerType;
import org.chenile.orchestrator.process.model.payload.StartProcessingPayload;
import org.chenile.orchestrator.process.model.payload.SubProcessPayload;
import org.chenile.orchestrator.process.workers.WorkListenerInput;

/** Base for a process-specific splitter that receives queue work directly. */
public abstract class SplitterWorkListener<Input, ChildInput>
    extends AbstractWorkListener<Input> {
  /**
   * Creates the child process payloads. Implementations can call
   * {@link #splitPartiallyDone(WorkListenerInput, List)} to submit batches incrementally and can
   * use {@link #makeSubProcessPayload(Object, String)} to build each child payload.
   *
   * @param input the work message, including the typed payload and dispatch context
   * @return the child process payloads to submit with the split completion event
   */
  protected abstract List<SubProcessPayload> doStart(WorkListenerInput<Input> input);

  @Override
  protected WorkerType expectedWorkerType() {
    return WorkerType.SPLITTER;
  }

  @Override
  protected String errorEventId() {
    return Constants.Events.SPLIT_DONE_WITH_ERRORS;
  }

  @Override
  protected void doWork(WorkListenerInput<Input> input) throws Exception {
    logger.info("Splitter work received for processId={} input={}", input.processId, input.payload);
    StartProcessingPayload payload = new StartProcessingPayload();
    payload.subProcesses = doStart(input);
    processManagerClient.splitDone(input.processId, payload);
  }

  protected void splitPartiallyDone(
      WorkListenerInput<?> input, List<SubProcessPayload> subProcesses) {
    StartProcessingPayload payload = new StartProcessingPayload();
    payload.subProcesses = subProcesses;
    processManagerClient.splitPartiallyDone(input.processId, payload);
  }

  protected SubProcessPayload makeSubProcessPayload(ChildInput child, String childProcessType) {
    SubProcessPayload payload = new SubProcessPayload();
    payload.processType = childProcessType;
    try {
      payload.args = objectMapper.writeValueAsString(child);
    } catch (Exception exception) {
      logger.error("Unable to construct args for process type={}", childProcessType, exception);
    }
    return payload;
  }
}
