package org.chenile.orchestrator.process.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerType;
import org.chenile.orchestrator.process.model.payload.AggregationDonePayload;
import org.chenile.orchestrator.process.utils.ProcessUtil;
import org.chenile.orchestrator.process.workers.WorkListenerInput;

/** Base for a process-specific aggregator that receives queue work directly. */
public abstract class AggregatorWorkListener<Input, Output, ChildOutput>
    extends AbstractWorkListener<Input> {

  /**
   * Called once for each child process to progressively build the aggregate output. The first call
   * receives {@code null} for {@code out}; later calls receive the output returned by the previous
   * call.
   *
   * @param out the aggregate output built so far
   * @param input the work message for this process
   * @param childOutput the typed output of the current child process
   * @param childProcess the child process descriptor
   * @return the aggregate output to pass to the next child
   */
  protected abstract Output doStart(
      Output out,
      WorkListenerInput<Input> input,
      ChildOutput childOutput,
      Process childProcess);

  @Override
  protected WorkerType expectedWorkerType() {
    return WorkerType.AGGREGATOR;
  }

  @Override
  protected String errorEventId() {
    return Constants.Events.AGGREGATION_DONE_WITH_ERRORS;
  }

  @Override
  protected void doWork(WorkListenerInput<Input> input) throws Exception {
    logger.info("Aggregator work received for processId={} input={}", input.processId, input.payload);
    List<Process> processes = processManagerClient.getSubProcesses(input.processId, false);
    Output output = null;
    for (Process childProcess : processes) {
      if (childProcess.getId().equals(input.processId)) {
        continue;
      }
      ChildOutput childOutput = deserializeChildOutput(childProcess);
      output = doStart(output, input, childOutput, childProcess);
    }
    AggregationDonePayload payload = new AggregationDonePayload();
    payload.output = objectMapper.writeValueAsString(output);
    processManagerClient.aggregationDone(input.processId, payload);
  }

  private ChildOutput deserializeChildOutput(Process childProcess) throws JsonProcessingException {
    if (childProcess.output == null) {
      return null;
    }
    ProcessUtil.TypeInfo<ChildOutput> typeInfo = ProcessUtil.deriveType(this, "doStart", 2);
    if (typeInfo == null) {
      throw new IllegalStateException("Unable to derive aggregator child output type");
    }
    if (typeInfo.isString) {
      @SuppressWarnings("unchecked")
      ChildOutput output = (ChildOutput) childProcess.output;
      return output;
    }
    return objectMapper.readValue(childProcess.output, typeInfo.type);
  }

}
