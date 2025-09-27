package org.chenile.orchestrator.process.service.entry;

import org.apache.commons.logging.Log;
import org.chenile.orchestrator.process.configuration.dao.ProcessRepository;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.service.defs.PostSaveHook;
import org.chenile.stm.impl.STMActionsInfoProvider;
import org.chenile.utils.entity.service.EntityStore;
import org.chenile.workflow.api.StateEntityService;
import org.chenile.workflow.service.stmcmds.GenericEntryAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class ProcessEntryAction extends GenericEntryAction<Process> {
    Logger logger = LoggerFactory.getLogger(ProcessEntryAction.class);
    @Autowired
    NotifyParent notifyParent;
    @Autowired
    PostSaveHook postSaveHook;
    @Autowired
    StateEntityService<Process> processService ;
    @Autowired
    private ProcessRepository processRepository;
    public ProcessEntryAction(EntityStore<Process> entityStore, STMActionsInfoProvider stmActionsInfoProvider) {
        super(entityStore, stmActionsInfoProvider);
    }

    @Override
    public void execute(Process process) throws Exception {
        super.execute(process);
        String currentState = process.getCurrentState().getStateId();
        switch(currentState){
            case Constants.SUB_PROCESSES_PENDING_STATE:
                createSubProcesses(process);
                activateSuccessors(process);
                break;
            // intimate the parent that we are done
            case Constants.PROCESSED_STATE:
                activateSuccessors(process);
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

    /**
     * Since process.subProcesses is transient, it will only be initialized when
     * the splitDone event is received. Hence, sub processes will only be created
     * when the solitDone event is received.
     * @param process
     */
    private void createSubProcesses(Process process) {
        if (process.subProcesses == null || process.subProcesses.isEmpty()) return;
        for (Process p: process.subProcesses){
            processService.create(p);
        }
    }

    private void activateSuccessors(Process process){
        if(process.childIdToActivateSuccessors == null) return;
        List<Process> predecessorList =  processRepository.findByPredecessorId(process.childIdToActivateSuccessors);
        for(Process predecessor: predecessorList){
            processService.processById(predecessor.getId(), Constants.ACTIVATE_DORMANT_EVENT, null);
        }
    }
}
