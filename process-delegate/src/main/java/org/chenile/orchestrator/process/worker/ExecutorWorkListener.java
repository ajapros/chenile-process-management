package org.chenile.orchestrator.process.worker;

import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.WorkerType;
import org.chenile.orchestrator.process.model.payload.DoneSuccessfullyPayload;
import org.chenile.orchestrator.process.workers.WorkListenerInput;

/** Base for a process-specific executor that receives queue work directly. */
public abstract class ExecutorWorkListener<Input, Output>
    extends AbstractWorkListener<Input> {

  /**
   * Converts the input payload into the process output. Throw an exception when processing fails;
   * structured errors can be reported by throwing an {@code ErrorNumException}. Call
   * {@link #progressUpdate(WorkListenerInput, int)} to report progress.
   *
   * @param input the work message, including the typed payload and dispatch context
   * @return the output to send back to the process manager
   */
  protected abstract Output doStart(WorkListenerInput<Input> input);

  @Override
  protected WorkerType expectedWorkerType() {
    return WorkerType.EXECUTOR;
  }

  @Override
  protected String errorEventId() {
    return Constants.Events.DONE_WITH_ERRORS;
  }

  @Override
  protected void doWork(WorkListenerInput<Input> input) throws Exception {
    logger.info("Executor work received for processId={} input={}", input.processId, input.payload);
    Output output = doStart(input);
    DoneSuccessfullyPayload payload = new DoneSuccessfullyPayload();
    payload.output = objectMapper.writeValueAsString(output);
    processManagerClient.doneSuccessfully(input.processId, payload);
  }
}
