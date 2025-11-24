package org.chenile.orchestrator.process.service.entry;


import org.chenile.orchestrator.process.configuration.dao.ProcessRepository;

import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.service.defs.PostSaveHook;
import org.chenile.stm.State;
import org.chenile.stm.impl.STMActionsInfoProvider;
import org.chenile.utils.entity.service.EntityStore;
import org.chenile.workflow.api.StateEntityService;
import org.chenile.workflow.service.stmcmds.GenericEntryAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;

public class ProcessEntryAction extends GenericEntryAction<Process> {
    Logger logger = LoggerFactory.getLogger(ProcessEntryAction.class);
    @Autowired
    NotifyParent notifyParent;
    @Autowired
    PostSaveHook postSaveHook;
    @Autowired @Qualifier("_processStateEntityService_")
    StateEntityService<Process> processService ;
    @Autowired
    private ProcessRepository processRepository;
    public ProcessEntryAction(EntityStore<Process> entityStore, STMActionsInfoProvider stmActionsInfoProvider) {
        super(entityStore, stmActionsInfoProvider);
    }

    @Override
    public void execute(State startState, State endState, Process process) throws Exception {
        super.execute(startState,endState,process);
        String currentState = process.getCurrentState().getStateId();
        switch(currentState){
            case Constants.States.SPLITTING_AND_WAITING_SUBPROCESSES:
                createSubProcesses(process);
                activateSuccessors(process);
                break;
            // intimate the parent that we are done
            case Constants.States.PROCESSED:
                activateSuccessors(process);
                notifyParent.notifyParentDone(process);
                break;
            case Constants.States.PROCESSED_WITH_ERRORS:
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
     * when the splitDone event is received.
     * @param process - the process for which sub processes need to be created
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
            processService.processById(predecessor.getId(), Constants.Events.ACTIVATE, null);
        }
    }
}
