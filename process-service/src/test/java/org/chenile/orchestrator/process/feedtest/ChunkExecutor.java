package org.chenile.orchestrator.process.feedtest;

import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.payload.DoneSuccessfullyPayload;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.model.WorkerType;
import org.chenile.workflow.api.StateEntityService;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

public class ChunkExecutor implements WorkerStarter {
    @Autowired
    StateEntityService<Process> processManager ;
    @Override
    public void start(Process process, Map<String, String> execDef, WorkerType workerType) {
        // Assert if the executor args are passed from def.json to this method
        Assert.assertEquals("executor_value",execDef.get("executor_key"));
        DoneSuccessfullyPayload payload = new DoneSuccessfullyPayload();
        processManager.processById(process.getId(), Constants.Events.EXECUTION_COMPLETED_EVENT,payload);
    }
}
