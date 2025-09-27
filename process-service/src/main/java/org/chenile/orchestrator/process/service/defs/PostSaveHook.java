package org.chenile.orchestrator.process.service.defs;

import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerType;
import org.chenile.orchestrator.process.service.ProcessInitializeStateService;
import org.chenile.utils.entity.service.EntityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Map;

/**
 * This class handles specific things that need to be done to kick-start the
 * WorkerStarter with the correct arguments.
 */
public class PostSaveHook {
    Logger logger = LoggerFactory.getLogger(PostSaveHook.class);
    @Autowired  ProcessConfigurator processConfigurator;

    @Autowired
    private ProcessInitializeStateService processInitializeStateService;


    public void setWorkerStarter(WorkerStarter workerStarter) {
        this.workerStarter = workerStarter;
    }
    WorkerStarter workerStarter;

    public void execute(Process process) {
        String processType = process.processType;
        String currentState = process.getCurrentState().getStateId();

        if(process.initializedStates.contains(currentState)){
            logger.debug("Process {} is already initialized for state {}, not executing PostSaveHook",process.id,currentState);
            return;
        }else{
            process.initializedStates.add(currentState);
            processInitializeStateService.markStateAsInitialized(process.getId(), currentState);
        }

       /* if(process.skipPostWorkerCreation)
            return;*/

        ProcessDef processDef = processConfigurator.processes.processMap.get(processType);
        if(processDef == null) return;
        if(workerStarter == null) return;
        Map<String,String> params = null;
        WorkerType workerType ;
        // Execute the correct type of worker that will lead to the next state transition
        switch(currentState){
            case Constants.SPLIT_PENDING_STATE:
                workerType = WorkerType.SPLITTER;
                params = processDef.splitterConfig;
                break;
            case Constants.AGGREGATION_PENDING_STATE:
                workerType = WorkerType.AGGREGATOR;
                params = processDef.aggregatorConfig;
                break;
            case Constants.EXECUTING_STATE:
                params = processDef.executorConfig;
                workerType = WorkerType.EXECUTOR;
                break;
            default:
                return;
        }
        workerStarter.start(process,params,workerType);

    }
}
