package org.chenile.orchestrator.process.service.cmds;

import org.apache.commons.lang3.StringUtils;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.SubProcessError;
import org.chenile.orchestrator.process.model.payload.ErrorPayload;
import org.chenile.orchestrator.process.model.payload.common.ProcessCreationPayload;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.chenile.workflow.service.stmcmds.AbstractSTMTransitionAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public abstract class BaseProcessAction<T> extends AbstractSTMTransitionAction<Process, T> {
    private static final Logger logger = LoggerFactory.getLogger(BaseProcessAction.class);


    protected final void logActionInvocation(Process process, String eventId, State startState, State endState) {
        logger.info("ACTION INVOKED: {} for processId={}, event='{}' (Transitioning: {} -> {})",
                this.getClass().getSimpleName(),
                process.getId(),
                eventId,
                startState != null ? startState.getStateId() : "null",
                endState != null ? endState.getStateId() : "null"
        );
    }



    protected void addSubProcessesToParent(Process parentProcess, List<Process> newSubProcesses) {
        if (parentProcess == null || newSubProcesses == null || newSubProcesses.isEmpty()) {
            return;
        }
        parentProcess.getSubProcessesToCreate().addAll(newSubProcesses);
        parentProcess.setNumSubProcesses(parentProcess.getNumSubProcesses() + newSubProcesses.size());
    }

    /**
     * A generic, reusable factory method to create Process entities from payloads.
     * It does NOT attach them to a parent; it only creates and returns them.
     *
     * @param parentId The ID of the parent for the new processes.
     * @param payloads The list of creation requests.
     * @return A list of new, populated Process entities.
     */
    protected List<Process> createProcessesFromPayloads(String parentId, List<ProcessCreationPayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return Collections.emptyList();
        }
        return payloads.stream()
                .map(payload -> {
                    Process newProcess = new Process();
                    if (payload.getWorkerSuppliedId() != null) {
                        newProcess.setId(payload.getWorkerSuppliedId());
                    }
                    newProcess.setProcessType(payload.getProcessType());
                    newProcess.setArgs(payload.getArgs());
                    newProcess.setLeaf(payload.isLeaf());
                    newProcess.setParentId(parentId); // Set the provided parent ID
                    return newProcess;
                })
                .collect(Collectors.toList());
    }

    /**
     * Creates a SubProcessError entity from an ErrorPayload and adds it to the
     * specified process's list of errors.
     *
     * @param process The process that encountered the error.
     * @param payload The error payload from the failed event.
     */
    protected void addErrorToProcess(Process process, ErrorPayload payload) {
        if (process == null || payload == null) {
            return;
        }
        boolean hasErrors =
                (payload.getValidationErrors() != null && !payload.getValidationErrors().isEmpty()) ||
                        StringUtils.isNotBlank(payload.getExceptionMessage()) ||
                        StringUtils.isNotBlank(payload.getStackTrace());

        if (!hasErrors) {
            return; // nothing to record
        }

        SubProcessError subProcessError = new SubProcessError();
        subProcessError.setProcessId(process.getId());
        subProcessError.setTimeOfCompletion(new Date());
        subProcessError.setValidationErrors(payload.getValidationErrors());
        subProcessError.setExceptionMessage(payload.getExceptionMessage());
        subProcessError.setStackTrace(payload.getStackTrace());
        process.getErrors().add(subProcessError);
    }
}
