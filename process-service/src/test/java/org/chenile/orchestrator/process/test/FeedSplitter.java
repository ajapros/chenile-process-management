package org.chenile.orchestrator.process.test;

import org.chenile.orchestrator.process.model.*;
import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.StartProcessingPayload;
import org.chenile.orchestrator.process.model.payload.SubProcessPayload;
import org.chenile.workflow.api.StateEntityService;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FeedSplitter implements WorkerStarter {
    /**
     * This gives a configuration option to change the #files as part of
     * individual test cases. See {@link TestFeeds} for the usage of this parameter.
     */
    public static int numFiles = 1;
    public static int batchSize = 10;
    @Autowired
    StateEntityService<Process> processManager ;

    @Override
    public void start(Process process, Map<String, String> execDef, WorkerType workerType) {
        List<SubProcessPayload> allSubProcesses = new ArrayList<>();
        for (int i = 0; i < numFiles; i++) {
            allSubProcesses.add(createSubProcessPayload(process.getId(), i, execDef));
        }

        // Loop through the list of all sub-processes and send them in batches of 'batchSize'.
        for (int i = 0; i < allSubProcesses.size(); i += batchSize) {
            if(i > 0) {
                try {
                    // Adding a small sleep to simulate some delay between batches.
                    Thread.sleep(100); // 100 milliseconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            StartProcessingPayload batchPayload = new StartProcessingPayload();
            // Create a sublist for the current batch
            int end = Math.min(i + batchSize, allSubProcesses.size());
            batchPayload.subProcesses = allSubProcesses.subList(i, end);
            // Send the current batch
            processManager.processById(process.getId(), Constants.SPLIT_UPDATE_EVENT, batchPayload);

        }

        // Always send splitDone at the end to signal completion.
        processManager.processById(process.getId(), Constants.SPLIT_DONE_EVENT, new StartProcessingPayload());
    }

    private SubProcessPayload createSubProcessPayload(String parentId, int index, Map<String, String> execDef) {
        SubProcessPayload p = new SubProcessPayload();
        p.processType = "file";
        p.childId = parentId + "FILE" + (index + 1);
        p.args = """
                { "filename" : "file%d" }
                """.formatted(index + 1);
        Assert.assertEquals("splitter_value", execDef.get("splitter_key"));
        return p;
    }





/*    @Override
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
            processManager.processById(process.getId(), Constants.SPLIT_UPDATE_EVENT,payload1);
        }
        processManager.processById(process.getId(), Constants.SPLIT_DONE_EVENT,payload);
    }*/
}
