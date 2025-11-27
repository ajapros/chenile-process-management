package org.chenile.orchestrator.process.service.defs;

import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.WorkerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

/**
 * This class handles specific things that need to be done to kick-start the
 * WorkerStarter with the correct arguments.
 */
public class PostSaveHook {
    Logger logger = LoggerFactory.getLogger(PostSaveHook.class);
    @Autowired  ProcessConfigurator processConfigurator;

    public void setWorkerStarter(WorkerStarter workerStarter) {
        this.workerStarter = workerStarter;
    }
    WorkerStarter workerStarter;

    public void execute(Process process) {
        if(process.skipPostWorkerCreation)
            return;
        String processType = process.processType;
        String currentState = process.getCurrentState().getStateId();
        ProcessDef processDef = processConfigurator.processes.processMap.get(processType);
        if(processDef == null) return;
        if(workerStarter == null) return;
        Map<String,String> params = null;
        WorkerType workerType ;
        // Execute the correct type of worker that will lead to the next state transition
        switch(currentState){
            case Constants.States.SPLIT_PENDING:
                workerType = WorkerType.SPLITTER;
                params = processDef.splitterConfig;
                break;
            case Constants.States.AGGREGATION_PENDING:
                workerType = WorkerType.AGGREGATOR;
                params = processDef.aggregatorConfig;
                break;
            case Constants.States.EXECUTING:
                params = processDef.executorConfig;
                workerType = WorkerType.EXECUTOR;
                break;
            default:
                return;
        }
        logger.info("State = {}. Starting worker type = {}",
                currentState,workerType);
        WorkerDto workerDto = new WorkerDto();
        workerDto.process = process;
        workerDto.execDef = params;
        workerDto.workerType = workerType;
        workerStarter.start(workerDto);
    }
}
