package org.chenile.orchestrator.process;

import org.chenile.orchestrator.process.model.WorkerDto;

public interface WorkerStarter {
  public void start(WorkerDto workerDto);
}
