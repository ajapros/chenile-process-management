package org.chenile.orchestrator.process;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerType;

import java.util.Map;
public interface WorkerStarter {
    void start(Process process, Map<String, String> execDef, WorkerType workerType);
}
