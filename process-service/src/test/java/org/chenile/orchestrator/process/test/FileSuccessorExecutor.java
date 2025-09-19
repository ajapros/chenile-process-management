package org.chenile.orchestrator.process.test;

import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.DoneSuccessfullyPayload;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerType;
import org.chenile.workflow.api.StateEntityService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

public class FileSuccessorExecutor implements WorkerStarter {

    @Autowired
    StateEntityService<Process> processManager ;

    @Override
    public void start(Process process, Map<String, String> execDef, WorkerType workerType) {
        DoneSuccessfullyPayload payload = new DoneSuccessfullyPayload();
        processManager.processById(process.getId(), Constants.DONE_EVENT,payload);
    }
}
