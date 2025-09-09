package org.chenile.orchestrator.process.service.defs;

import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

/**
 * This class handles specific things that need to be done to kick-start the
 * WorkerStarter with the correct arguments.
 *  start the correct worker associated with the new state
 */
public class PostSaveHook {
    private static final Logger logger = LoggerFactory.getLogger(PostSaveHook.class);
    @Autowired
    ProcessConfigurator processConfigurator;

    public void setWorkerStarter(WorkerStarter workerStarter) {
        this.workerStarter = workerStarter;
    }
    WorkerStarter workerStarter;

    public void execute(Process process) {
        if (workerStarter == null || process == null || process.getCurrentState() == null){
            logger.warn("PostSaveHook: WorkerStarter or Process or CurrentState is null, cannot proceed.");
            return;
        }

        String processType = process.getProcessType();
        String currentState = process.getCurrentState().getStateId();
        ProcessDef processDef = processConfigurator.getProcessDef(processType);

        if (processDef == null){
            logger.warn("PostSaveHook: ProcessDef is null for processType: {} " , processType);
            return;
        }

        Map<String, String> params;
        WorkerType workerType;

        switch (currentState) {
            case Constants.States.INITIALIZING_SPLIT:
                workerType = WorkerType.SPLITTER;
                params = processDef.getSplitterConfig();
                break;
            case Constants.States.INITIALIZING_EXECUTION:
                params = processDef.getExecutorConfig();
                workerType = WorkerType.EXECUTOR;
                break;
            case Constants.States.INITIALIZING_AGGREGATION:
                workerType = WorkerType.AGGREGATOR;
                params = processDef.getAggregatorConfig();
                break;
            case Constants.States.INITIALIZING_SUCCESSOR:
                workerType = WorkerType.CHAINER;
                params = processDef.getChainerConfig();
                break;
            default:
                logger.debug("PostSaveHook: No workerStarter found for processId: {} processType: {} currentState: {} ", process.id, processType, currentState);
                return;
        }
        logger.info("PostSaveHook: Starting workerStarter for processId:{} processType: {} with workerType: {}", process.id, processType, workerType);
        workerStarter.start(process, params, workerType);
    }
}
