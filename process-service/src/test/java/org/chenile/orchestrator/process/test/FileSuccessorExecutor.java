package org.chenile.orchestrator.process.test;

import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.payload.DoneSuccessfullyPayload;
import org.chenile.workflow.api.StateEntityService;
import org.springframework.beans.factory.annotation.Autowired;

public class FileSuccessorExecutor implements WorkerStarter {

    @Autowired
    StateEntityService<Process> processManager;

    @Override
    public void start(WorkerDto workerDto) {
        DoneSuccessfullyPayload payload = new DoneSuccessfullyPayload();
        processManager.processById(workerDto.process.getId(), Constants.Events.DONE_SUCCESSFULLY, payload);
    }
}
