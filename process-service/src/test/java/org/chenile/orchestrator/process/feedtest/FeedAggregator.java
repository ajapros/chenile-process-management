package org.chenile.orchestrator.process.feedtest;

import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.model.WorkerType;
import org.chenile.workflow.api.StateEntityService;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

public class FeedAggregator implements WorkerStarter {
    @Autowired
    StateEntityService<Process> processManager ;
    @Override
    public void start(Process process, Map<String, String> execDef, WorkerType workerType) {
        // Assert if the aggregator args are passed from def.json to this method
        Assert.assertEquals("aggregator_value",execDef.get("aggregator_key"));
        processManager.processById(process.getId(), Constants.AGGREGATION_DONE_EVENT,null);
    }
}
