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

public class FeedSplitter implements WorkerStarter {
    /**
     * This gives a configuration option to change the #files as part of
     * individual test cases. See TestFeeds for the usage of this parameter.
     */
    public static int numFiles = 1;
    @Autowired
    StateEntityService<Process> processManager ;

    @Override
    public void start(Process process, Map<String, String> execDef, WorkerType workerType) {
        StartProcessingPayload payload = new StartProcessingPayload();
        List<ProcessCreationPayload> subProcesses = new ArrayList<>();
        for (int i = 0; i < numFiles; i++) {
            ProcessCreationPayload p = new ProcessCreationPayload();
            p.setProcessType("file");
            p.setWorkerSuppliedId(process.getId() + "FILE" + (i + 1));
            p.setArgs("""
                    { "filename" : "file%d" }
                    """.formatted(i + 1));
            subProcesses.add(p);
        }
        payload.setSubProcesses(subProcesses);
        processManager.processById(process.getId(), Constants.Events.SPLIT_COMPLETED_EVENT, payload);
    }
}
