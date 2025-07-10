package org.chenile.orchestrator.process.feedtest;

import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.StartProcessingPayload;
import org.chenile.orchestrator.process.model.SubProcessPayload;
import org.chenile.orchestrator.process.service.defs.WorkerStarter;
import org.chenile.workflow.api.StateEntityService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Map;

public class FileChunker implements WorkerStarter {
    @Autowired
    StateEntityService<Process> processManager ;
    @Override
    public void start(Process process,Map<String, String> execDef,String workerType) {
        StartProcessingPayload payload = new StartProcessingPayload();
        payload.subProcesses = new ArrayList<>();
        SubProcessPayload p = new SubProcessPayload();
        p.processType = "chunk";
        p.childId = process.id + "CHUNK1";
        payload.subProcesses.add(p);
        processManager.processById(process.getId(), Constants.SPLIT_DONE,payload);
    }
}
