package org.chenile.orchestrator.process.model;

import java.util.Map;

/**
 * The Model passed to the Worker Starter.
 */
public class WorkerDto {
    public Process process;
    public Map<String,String> execDef;
    public WorkerType workerType;
}
