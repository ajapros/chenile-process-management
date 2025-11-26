package org.chenile.orchestrator.process.test;

import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.WorkerType;
import org.chenile.orchestrator.process.model.payload.DoneSuccessfullyPayload;
import org.chenile.workflow.api.StateEntityService;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

public class ChunkExecutor implements WorkerStarter {
    @Autowired
    StateEntityService<Process> processManager ;
    // Store the successor ID so that we can assert that a successor has been created.
    // This is useful for the test assertion.
    @Override
    public void start(WorkerDto workerDto) {
        // Assert if the executor args are passed from def.json to this method
        Assert.assertEquals("executor_value",workerDto.execDef.get("executor_key"));
        DoneSuccessfullyPayload payload = new DoneSuccessfullyPayload();

        processManager.processById(workerDto.process.getId(), Constants.Events.DONE_SUCCESSFULLY,payload);
    }
}
