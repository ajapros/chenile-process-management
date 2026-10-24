package org.chenile.orchestrator.process.worker;

import org.chenile.base.exception.ConfigurationException;
import org.chenile.orchestrator.process.workers.WorkListenerInput;
import org.chenile.orchestrator.process.workers.WorkListenerService;

/** Fails clearly if process work reaches the default service instead of the selected worker bean. */
public class ProcessWorkServiceFallback implements WorkListenerService<Object> {
  @Override
  public boolean accept(
      String processType, String workerType, WorkListenerInput<Object> input) {
    throw new ConfigurationException(
        "NO_PROCESS_WORKER_SELECTED",
        "No process worker selected for processType="
            + processType
            + " workerType="
            + workerType);
  }
}
