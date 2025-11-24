package org.chenile.orchestrator.process.test;

import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.WorkerType;
import org.chenile.orchestrator.process.model.payload.AggregationDonePayload;
import org.chenile.workflow.api.StateEntityService;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

public class FeedAggregator implements WorkerStarter {
    @Autowired
    StateEntityService<Process> processManager ;
    @Override
    public void start(WorkerDto workerDto) {
        // Assert if the aggregator args are passed from def.json to this method
        Assert.assertEquals("aggregator_value",workerDto.execDef.get("aggregator_key"));
        processManager.processById(workerDto.process.getId(), Constants.Events.AGGREGATION_DONE, new AggregationDonePayload());
    }
}
