package org.chenile.orchestrator.process.api;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.ProcessDto;
import org.chenile.orchestrator.process.model.ProcessCompletedEvent;
import org.chenile.workflow.api.StateEntityService;
import org.chenile.workflow.dto.StateEntityServiceResponse;

import java.util.List;

public interface ProcessManager extends StateEntityService<Process> {
    public List<Process> getSubProcesses(String processId, boolean recursive);
    StateEntityServiceResponse<Process> create(ProcessDto processDto);
    List<StateEntityServiceResponse<Process>> processCompleted(ProcessCompletedEvent event);
}
