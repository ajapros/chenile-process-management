package org.chenile.orchestrator.process.service.defs;

import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

/**
 * This class calls a post save hook that can be explicitly
 * configured for each state
 */
public class PostSaveHook {
    @Autowired  ProcessConfigurator processConfigurator;

    public void setWorkerStarter(WorkerStarter workerStarter) {
        this.workerStarter = workerStarter;
    }
    WorkerStarter workerStarter;

    public void execute(Process process) {
        String processType = process.processType;
        String currentState = process.getCurrentState().getStateId();
        ProcessDef processDef = processConfigurator.processes.processMap.get(processType);
        // extract the action to call post this state. That action will
        // ultimately send the event that leads this state machine to the next state
        switch(currentState){
            case Constants.SPLIT_PENDING_STATE:
                processSplit(process,processDef);
                break;
            case Constants.AGGREGATION_PENDING_STATE:
                processAggregation(process,processDef);
                break;
            case Constants.EXECUTING_STATE:
                processExecutor(process,processDef);
                break;
        }
    }

    private void processExecutor(Process process, ProcessDef processDef) {
        Map<String,String> params = null;
        if(processDef != null)
            params = processDef.executorConfig;
        if (workerStarter != null)
            workerStarter.start(process,params,Constants.EXECUTOR);
    }

    private void processAggregation(Process process, ProcessDef processDef) {
        Map<String,String> params = null;
        if(processDef != null)
            params = processDef.aggregatorConfig;
        if (workerStarter != null)
            workerStarter.start(process,params,Constants.AGGREGATOR);
    }

    private void processSplit(Process process, ProcessDef processDef) {
        Map<String,String> params = null;
        if(processDef != null)
            params = processDef.splitterConfig;
        if (workerStarter != null)
            workerStarter.start(process,params,Constants.SPLITTER);
    }


}
