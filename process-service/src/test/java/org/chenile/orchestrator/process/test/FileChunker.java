package org.chenile.orchestrator.process.test;

import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.payload.StartProcessingPayload;
import org.chenile.orchestrator.process.model.payload.SubProcessPayload;
import org.chenile.workflow.api.StateEntityService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;

public class FileChunker implements WorkerStarter {
    @Autowired
    StateEntityService<Process> processManager ;
    @Override
    public void start(WorkerDto workerDto) {
        StartProcessingPayload payload = new StartProcessingPayload();
        payload.subProcesses = new ArrayList<>();
        SubProcessPayload p = new SubProcessPayload();
        p.processType = "chunk";
        p.childId = workerDto.process.id + "CHUNK1";
        payload.subProcesses.add(p);
        processManager.processById(workerDto.process.getId(), Constants.Events.SPLIT_DONE,payload);
    }
}
