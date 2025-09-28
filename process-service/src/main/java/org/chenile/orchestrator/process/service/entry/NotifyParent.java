package org.chenile.orchestrator.process.service.entry;

import org.apache.commons.lang3.StringUtils;
import org.chenile.orchestrator.process.model.*;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.DoneSuccessfullyPayload;
import org.chenile.orchestrator.process.model.payload.SubProcessDoneWithErrorsPayload;
import org.chenile.workflow.api.StateEntityService;

import java.util.List;
import java.util.stream.Collectors;

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
        payload.childId = process.id;
        stateEntityService.processById(process.parentId, Constants.Events.SUB_PROCESS_DONE_SUCCESSFULLY,payload);
    }

  /*  public void notifyParentDoneWithErrors(Process process){
        if (process.parentId == null) return;
        SubProcessDoneWithErrorsPayload payload = new SubProcessDoneWithErrorsPayload();
        payload.childId = process.id;
        // clone the error so that all the IDs of this entity are not passed to the parent.
        payload.errors = process.errors.stream().map(SubProcessError::clone).toList();
        stateEntityService.processById(process.parentId,Constants.SUB_PROCESS_DONE_WITH_ERRORS_EVENT,payload);
    }*/

    public void notifyParentDoneWithErrors(Process process) {
        if (process == null || process.parentId == null) {
            return;
        }
        SubProcessDoneWithErrorsPayload payload = buildErrorPayloadFromProcess(process);
        stateEntityService.processById(process.parentId, Constants.Events.SUB_PROCESS_DONE_WITH_ERRORS, payload);
    }

    private SubProcessDoneWithErrorsPayload buildErrorPayloadFromProcess(Process process) {
        // Guarantees a non-null payload is always created.
        SubProcessDoneWithErrorsPayload payload = new SubProcessDoneWithErrorsPayload();

        if (process == null || process.errors == null || process.errors.isEmpty()) {
            return payload; // Return the empty payload if there are no errors to process.
        }

        List<String> allValidationErrors = process.errors.stream()
                .flatMap(error -> error.getValidationErrors().stream())
                .collect(Collectors.toList());

        payload.setValidationErrors(allValidationErrors);

        // Find the first non-blank exception message and stack trace to report.
        process.errors.stream()
                .filter(error -> StringUtils.isNotBlank(error.getExceptionMessage()))
                .findFirst()
                .ifPresent(error -> {
                    payload.setExceptionMessage(error.getExceptionMessage());
                    payload.setStackTrace(error.getStackTrace());
                });

        return payload;
    }
}
