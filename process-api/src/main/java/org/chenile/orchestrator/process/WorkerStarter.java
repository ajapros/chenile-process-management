package org.chenile.orchestrator.process;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.WorkerType;

import java.util.Map;
public interface WorkerStarter {
    public void start(WorkerDto workerDto);
}
