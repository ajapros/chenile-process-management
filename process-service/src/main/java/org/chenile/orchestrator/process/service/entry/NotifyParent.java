package org.chenile.orchestrator.process.service.entry;

import org.chenile.orchestrator.process.model.*;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.workflow.api.StateEntityService;

/**
 * Notifies the parent process when the child process has finished. <br/>
 * This will enable the parent to do the following:<br/>
 * <ol>
 * <li>Update its status to reflect the fact that its sub-processes are completed</li>
 * <li>Kick off aggregation work.</li>
 * </ol>
 */
public class NotifyParent {
    public StateEntityService<Process> stateEntityService;
    public NotifyParent(StateEntityService<Process> stateEntityService){
        this.stateEntityService = stateEntityService;
    }
    public void notifyParentDone(Process process){
        if (process.parentId == null) return;
        DoneSuccessfullyPayload payload = new DoneSuccessfullyPayload();
        stateEntityService.processById(process.parentId, Constants.SUB_PROCESS_DONE_EVENT,payload);
    }

    public void notifyParentDoneWithErrors(Process process){
        if (process.parentId == null) return;
        SubProcessDoneWithErrorsPayload payload = new SubProcessDoneWithErrorsPayload();
        // clone the error so that all the IDs of this entity are not passed to the parent.
        payload.errors = process.errors.stream().map(SubProcessError::clone).toList();
        stateEntityService.processById(process.parentId,Constants.SUB_PROCESS_DONE_WITH_ERRORS_EVENT,payload);
    }
}
