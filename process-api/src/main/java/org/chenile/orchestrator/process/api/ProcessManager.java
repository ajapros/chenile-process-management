package org.chenile.orchestrator.process.api;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.workflow.api.StateEntityService;

import java.util.List;

public interface ProcessManager extends StateEntityService<Process> {
    public List<Process> getSubProcesses(String processId, boolean recursive);
}
