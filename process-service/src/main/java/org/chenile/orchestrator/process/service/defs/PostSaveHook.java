package org.chenile.orchestrator.process.service.defs;

import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerType;
import org.chenile.orchestrator.process.service.ProcessInitializeStateService;
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
    @Autowired
    ProcessConfigurator processConfigurator;

    @Autowired
    private ProcessInitializeStateService processInitializeStateService;


    public void setWorkerStarter(WorkerStarter workerStarter) {
        this.workerStarter = workerStarter;
    }

    WorkerStarter workerStarter;

    public void execute(Process process) {
        String processType = process.processType;
        String currentState = process.getCurrentState().getStateId();
        logger.debug("PostSaveHook | processId={} | type={} | state={}", process.getId(), processType, currentState);
        if (process.initializedStates.contains(currentState)) {
            logger.debug("PostSaveHook skipped — processId={}, type={}, currentState={} (already initialized)",
                    process.getId(), processType, currentState);
            return;
        }
        logger.info("Initializing worker for process ID {} in state '{}'", process.getId(), currentState);
        process.initializedStates.add(currentState);
        processInitializeStateService.markStateAsInitialized(process.getId(), currentState);

       /* if(process.skipPostWorkerCreation)
            return;*/

        ProcessDef processDef = processConfigurator.processes.processMap.get(processType);

        if (processDef == null) {
            logger.debug("PostSaveHook: Skipping worker start. Reason: ProcessDef is NULL for processType: {}", processType);
            return;
        }

        if (workerStarter == null) {
            logger.debug("PostSaveHook: Skipping worker start. Reason: workerStarter bean is NULL (ProcessType: {})", processType);
            return;
        }

        Map<String, String> params = null;
        WorkerType workerType;
        // Execute the correct type of worker that will lead to the next state transition
        switch (currentState) {
            case Constants.States.SPLITTING_AND_WAITING_SUBPROCESSES:
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
                logger.debug("PostSaveHook: No workerStarter found for processId: {} processType: {} currentState: {} ", process.id, processType, currentState);
                return;
        }
        logger.debug("PostSaveHook: Starting workerStarter for processId:{} processType: {} with workerType: {}", process.id, processType, workerType);
        workerStarter.start(process, params, workerType);

    }
}
