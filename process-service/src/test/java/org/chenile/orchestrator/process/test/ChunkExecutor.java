package org.chenile.orchestrator.process.test;

import org.chenile.orchestrator.process.model.*;
import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.DoneSuccessfullyPayload;
import org.chenile.orchestrator.process.model.payload.StatusUpdatePayload;
import org.chenile.workflow.api.StateEntityService;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

public class ChunkExecutor implements WorkerStarter {
    @Autowired
    StateEntityService<Process> processManager ;
    public static boolean sendStatusUpdate = false;

    // Store the successor ID so that we can assert that a successor has been created.
    // This is useful for the test assertion.
    @Override
    public void start(Process process, Map<String, String> execDef, WorkerType workerType) {
        // Assert if the executor args are passed from def.json to this method
        Assert.assertEquals("executor_value",execDef.get("executor_key"));

        if (sendStatusUpdate) {
            try {
                // Simulate work and send a 50% complete update
                Thread.sleep(10); // Simulate 10ms of work
                StatusUpdatePayload update1 = new StatusUpdatePayload();
                update1.percentComplete = 50;
                processManager.processById(process.getId(), Constants.STATUS_UPDATE_EVENT, update1);

                // Simulate more work and send a 90% complete update
                Thread.sleep(20);
                StatusUpdatePayload update2 = new StatusUpdatePayload();
                update2.percentComplete = 90;
                processManager.processById(process.getId(), Constants.STATUS_UPDATE_EVENT, update2);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        DoneSuccessfullyPayload payload = new DoneSuccessfullyPayload();

        processManager.processById(process.getId(), Constants.DONE_EVENT,payload);
    }
}
