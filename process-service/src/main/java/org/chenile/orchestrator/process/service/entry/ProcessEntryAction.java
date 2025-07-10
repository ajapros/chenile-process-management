package org.chenile.orchestrator.process.service.entry;

import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.service.defs.PostSaveHook;
import org.chenile.stm.impl.STMActionsInfoProvider;
import org.chenile.utils.entity.service.EntityStore;
import org.chenile.workflow.api.StateEntityService;
import org.chenile.workflow.service.stmcmds.GenericEntryAction;
import org.springframework.beans.factory.annotation.Autowired;

public class ProcessEntryAction extends GenericEntryAction<Process> {
    @Autowired
    NotifyParent notifyParent;
    @Autowired
    PostSaveHook postSaveHook;
    @Autowired
    StateEntityService<Process> processManager ;
    public ProcessEntryAction(EntityStore<Process> entityStore, STMActionsInfoProvider stmActionsInfoProvider) {
        super(entityStore, stmActionsInfoProvider);
    }

    @Override
    public void execute(Process process) throws Exception {
        super.execute(process);
        switch(process.getCurrentState().getStateId()){
            case Constants.SUB_PROCESSES_PENDING_STATE:
                createSubProcesses(process);
                break;
            // intimate the parent that we are done
            case Constants.PROCESSED_STATE:
                notifyParent.notifyParentDone(process);
                break;
            case Constants.PROCESSED_WITH_ERRORS_STATE:
                 notifyParent.notifyParentDoneWithErrors(process);
                 break;
            default:
                break;
        }
        postSaveHook.execute(process);
    }

    private void createSubProcesses(Process process) {
        for (Process p: process.subProcesses){
            processManager.create(p);
        }
    }
}
