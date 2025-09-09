package org.chenile.orchestrator.process.service.entry;

import org.chenile.orchestrator.process.model.*;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.service.defs.PostSaveHook;
import org.chenile.stm.impl.STMActionsInfoProvider;
import org.chenile.utils.entity.service.EntityStore;
import org.chenile.workflow.api.StateEntityService;
import org.chenile.workflow.param.MinimalPayload;
import org.chenile.workflow.service.stmcmds.GenericEntryAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class ProcessEntryAction extends GenericEntryAction<Process> {
    private static final Logger logger = LoggerFactory.getLogger(ProcessEntryAction.class);

    @Autowired
    NotifyParent notifyParent;
    @Autowired
    PostSaveHook postSaveHook;
    @Autowired
    StateEntityService<Process> processManager;

    public ProcessEntryAction(EntityStore<Process> entityStore, STMActionsInfoProvider stmActionsInfoProvider) {
        super(entityStore, stmActionsInfoProvider);
    }

    /**
     * Executes the entry action for the process.
     * Its only responsibility is to notify the parent process upon final completion.
     */
    @Override
    public void execute(Process process) throws Exception {
        super.execute(process);
        createPendingSubProcesses(process);
        switch (process.getCurrentState().getStateId()) {
            // intimate the parent that we are done
            case Constants.States.PROCESSED:
                notifyParent.notifyParentDone(process);
                break;
            case Constants.States.PROCESSED_WITH_ERRORS:
                notifyParent.notifyParentDoneWithErrors(process);
                break;
            default:
                // No immediate synchronous action needed for other states.
                break;
        }
        logger.debug("Running postSaveHook for processId: {}", process.getId());
        postSaveHook.execute(process);
    }

    private void createPendingSubProcesses(Process process) {
        if (process.getSubProcessesToCreate() == null || process.getSubProcessesToCreate().isEmpty()) {
            return;
        }
        for (Process subProcess : process.getSubProcessesToCreate()) {
            processManager.create(subProcess);
        }
        process.getSubProcessesToCreate().clear();
    }

}
