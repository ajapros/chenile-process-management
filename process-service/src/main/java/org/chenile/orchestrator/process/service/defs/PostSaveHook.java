package org.chenile.orchestrator.process.service.defs;

import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.config.reader.IProcessConfigurator;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.WorkerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;

/**
 * This class handles specific things that need to be done to kick-start the
 * WorkerStarter with the correct arguments.
 */
public class PostSaveHook {
    Logger logger = LoggerFactory.getLogger(PostSaveHook.class);
    @Autowired
    IProcessConfigurator processConfigurator;

    public void setWorkerStarter(WorkerStarter workerStarter) {
        this.workerStarter = workerStarter;
    }
    WorkerStarter workerStarter;

    public boolean hasWorkerStarter() { return workerStarter != null; }

    /** Resolve and snapshot work without executing it, for transactional outbox planning. */
    public WorkerDto prepare(Process process) {
        if(process.skipPostWorkerCreation)
            return null;
        String processType = process.processType;
        String currentState = process.getCurrentState().getStateId();
        ProcessDef processDef = processConfigurator.findByName(processType);
        if(processDef == null) return null;
        WorkerType workerType ;
        // Execute the correct type of worker that will lead to the next state transition
        switch(currentState){
            case Constants.States.SPLIT_PENDING:
                workerType = WorkerType.SPLITTER;
                break;
            case Constants.States.AGGREGATION_PENDING:
                workerType = WorkerType.AGGREGATOR;
                break;
            case Constants.States.EXECUTING:
                workerType = WorkerType.EXECUTOR;
                break;
            default:
                return null;
        }
        logger.info("State = {}. Starting worker type = {}",
                currentState,workerType);
        WorkerDto workerDto = new WorkerDto();
        workerDto.process = process;
        workerDto.execDef = new HashMap<>();
        if (processDef.config != null) workerDto.execDef.putAll(processDef.config);
        workerDto.workerType = workerType;
        return workerDto;
    }

    public void dispatch(WorkerDto dto) {
        if (workerStarter == null) throw new IllegalStateException("No WorkerStarter configured for durable dispatch");
        workerStarter.start(dto);
    }

}
