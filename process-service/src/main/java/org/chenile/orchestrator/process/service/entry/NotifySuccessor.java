package org.chenile.orchestrator.process.service.entry;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.common.SubProcessCompletedPayload;
import org.chenile.orchestrator.process.model.payload.common.SubProcessFailedPayload;
import org.chenile.orchestrator.process.model.payload.successor.SuccessorCreatedPayload;
import org.chenile.orchestrator.process.model.payload.successor.SuccessorFailedPayload;
import org.chenile.workflow.api.StateEntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A dedicated helper service responsible for sending confirmation events
 * (success or failure) back to an originating process after a successor
 * creation has been attempted.
 */
public class NotifySuccessor {
    private static final Logger logger = LoggerFactory.getLogger(NotifySuccessor.class);

    private final StateEntityService<Process> stateEntityService;

    public NotifySuccessor(StateEntityService<Process> stateEntityService){
        this.stateEntityService = stateEntityService;
    }

    /**
     * Notifies the originating process that its requested successor was created successfully.
     * @param originatingProcessId The ID of the process to notify.
     */
    public void notifySuccessorCreated(String originatingProcessId) {
        if (originatingProcessId == null) return;
        try {
            SuccessorCreatedPayload confirmationPayload = new SuccessorCreatedPayload();
            stateEntityService.processById(originatingProcessId, Constants.Events.SUCCESSOR_CREATED_EVENT, confirmationPayload);
            logger.info("Sent 'successorCreated' confirmation to originating processId: {}", originatingProcessId);
        } catch (Exception e) {
            logger.error("Failed to send 'successorCreated' confirmation to originating processId: {}", originatingProcessId, e);
        }
    }

    /**
     * Notifies the originating process that the creation of its requested successor failed.
     * @param originatingProcessId The ID of the process to notify.
     * @param parentProcessId The ID of the parent process that attempted the creation.
     * @param failureException The exception that caused the failure.
     */
    public void notifySuccessorCreationFailed(String originatingProcessId, String parentProcessId, Exception failureException) {
        if (originatingProcessId == null) return;
        try {
            SuccessorFailedPayload failedPayload = new SuccessorFailedPayload();
            failedPayload.setExceptionMessage("Parent process " + parentProcessId + " failed to create successor: " + failureException.getMessage());
            failedPayload.setStackTrace(ExceptionUtils.getStackTrace(failureException));
            stateEntityService.processById(originatingProcessId, Constants.Events.SUCCESSOR_FAILED_EVENT, failedPayload);
            logger.info("Sent 'successorFailed' notification to originating processId: {}", originatingProcessId);
        } catch (Exception e) {
            logger.error("Failed to send 'successorFailed' notification to originating processId: {}", originatingProcessId, e);
        }
    }


}
