package org.chenile.orchestrator.process.test;

import org.chenile.orchestrator.process.model.*;
import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.workflow.api.StateEntityService;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Map;

public class FeedSplitter implements WorkerStarter {
    /**
     * This gives a configuration option to change the #files as part of
     * individual test cases. See {@link TestFeeds} for the usage of this parameter.
     */
    public static int numFiles = 1;
    @Autowired
    StateEntityService<Process> processManager ;
    @Override
    public void start(Process process, Map<String, String> execDef, WorkerType workerType) {
        System.out.println("At the feed splitter. Process ID = " + process.id);
        StartProcessingPayload payload = new StartProcessingPayload();
        payload.subProcesses = new ArrayList<>();
        for (int i = 0; i < numFiles;i++) {
            StartProcessingPayload payload1 = new StartProcessingPayload();
            payload1.subProcesses = new ArrayList<>();
            SubProcessPayload p = new SubProcessPayload();
            p.processType = "file";
            p.childId = process.id + "FILE" + (i+1);
            p.args = """
                    { "filename" : "file%d" }
                    """.formatted(i+1);
            // Assert if the splitter args are passed from def.json to this method
            Assert.assertEquals("splitter_value",execDef.get("splitter_key"));
            payload1.subProcesses.add(p);
            processManager.processById(process.getId(), Constants.SPLIT_PARTIALLY_DONE_EVENT,payload1);
        }
        processManager.processById(process.getId(), Constants.SPLIT_DONE_EVENT,payload);
    }
}
