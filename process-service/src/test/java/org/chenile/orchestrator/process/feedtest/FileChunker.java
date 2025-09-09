package org.chenile.orchestrator.process.feedtest;

import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerType;
import org.chenile.orchestrator.process.model.payload.common.ProcessCreationPayload;
import org.chenile.orchestrator.process.model.payload.StartProcessingPayload;
import org.chenile.workflow.api.StateEntityService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FileChunker implements WorkerStarter {
    @Autowired
    StateEntityService<Process> processManager ;
    @Override
    public void start(Process process, Map<String, String> execDef, WorkerType workerType) {
        StartProcessingPayload payload = new StartProcessingPayload();
        List<ProcessCreationPayload> subProcesses = new ArrayList<>();
        ProcessCreationPayload p = new ProcessCreationPayload();
        p.setProcessType("chunk");
        p.setWorkerSuppliedId(process.getId() + "CHUNK1");
        subProcesses.add(p);
        payload.setSubProcesses(subProcesses);
        processManager.processById(process.getId(), Constants.Events.SPLIT_COMPLETED_EVENT, payload);
    }
}
