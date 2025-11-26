package org.chenile.orchestrator.process.test;

import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.payload.AggregationDonePayload;
import org.chenile.workflow.api.StateEntityService;
import org.springframework.beans.factory.annotation.Autowired;

public class FileAggregator implements WorkerStarter {
    @Autowired
    StateEntityService<Process> processManager ;
    @Override
    public void start(WorkerDto workerDto) {
        processManager.processById(workerDto.process.getId(), Constants.Events.AGGREGATION_DONE,new AggregationDonePayload());
    }
}
