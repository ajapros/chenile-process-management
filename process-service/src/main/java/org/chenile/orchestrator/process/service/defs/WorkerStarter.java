package org.chenile.orchestrator.process.service.defs;

import org.chenile.orchestrator.process.model.Process;

import java.util.Map;
public interface WorkerStarter {
    public void start(Process process, Map<String,String> execDef,String workerType);
}
