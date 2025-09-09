package org.chenile.orchestrator.process.service.entry;

import org.apache.commons.lang3.StringUtils;
import org.chenile.orchestrator.process.model.*;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.common.SubProcessCompletedPayload;
import org.chenile.orchestrator.process.model.payload.common.SubProcessFailedPayload;
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
    private final StateEntityService<Process> stateEntityService;

    public NotifyParent(StateEntityService<Process> stateEntityService){
        this.stateEntityService = stateEntityService;
    }

    public void notifyParentDone(Process process){
        if (process == null || process.getParentId() == null) return;

        SubProcessCompletedPayload payload = new SubProcessCompletedPayload();
        stateEntityService.processById(process.getParentId(), Constants.Events.SUBPROCESS_COMPLETED_EVENT, payload);
    }

    public void notifyParentDoneWithErrors(Process process) {
        if (process == null || process.getParentId() == null) {
            return;
        }

        SubProcessFailedPayload payload = buildErrorPayloadFromProcess(process);
        stateEntityService.processById(process.getParentId(), Constants.Events.SUCCESSOR_FAILED_EVENT, payload);
    }

    private SubProcessFailedPayload buildErrorPayloadFromProcess(Process process) {
        // Guarantees a non-null payload is always created.
        SubProcessFailedPayload payload = new SubProcessFailedPayload();

        if (process == null || process.getErrors() == null || process.getErrors().isEmpty()) {
            return payload; // Return the empty payload if there are no errors to process.
        }

        List<String> allValidationErrors = process.getErrors().stream()
                .flatMap(error -> error.getValidationErrors().stream())
                .collect(Collectors.toList());

        payload.setValidationErrors(allValidationErrors);

        // Find the first non-blank exception message and stack trace to report.
        process.getErrors().stream()
                .filter(error -> StringUtils.isNotBlank(error.getExceptionMessage()))
                .findFirst()
                .ifPresent(error -> {
                    payload.setExceptionMessage(error.getExceptionMessage());
                    payload.setStackTrace(error.getStackTrace());
                });

        return payload;
    }
}
