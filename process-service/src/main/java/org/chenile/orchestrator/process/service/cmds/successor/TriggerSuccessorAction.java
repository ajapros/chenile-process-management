package org.chenile.orchestrator.process.service.cmds.successor;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.successor.SuccessorCreatedPayload;
import org.chenile.orchestrator.process.model.payload.successor.SuccessorFailedPayload;
import org.chenile.orchestrator.process.model.payload.successor.TriggerSuccessorPayload;
import org.chenile.orchestrator.process.service.cmds.BaseProcessAction;
import org.chenile.orchestrator.process.service.entry.NotifySuccessor;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.chenile.workflow.api.StateEntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class TriggerSuccessorAction extends BaseProcessAction<TriggerSuccessorPayload> {
    private static final Logger logger = LoggerFactory.getLogger(TriggerSuccessorAction.class);

    private final NotifySuccessor notifySuccessor;

    public TriggerSuccessorAction(NotifySuccessor notifySuccessor) {
        this.notifySuccessor = notifySuccessor;
    }

    @Override
    public void transitionTo(Process process,
                             TriggerSuccessorPayload payload,
                             State startState, String eventId,
                             State endState, STMInternalTransitionInvoker<?> stm, Transition transition) {
        logActionInvocation(process, eventId, startState, endState);

        String originatingProcessId = payload.getOriginatingProcessId();
        try {
            if (originatingProcessId == null) {
                // This is a critical failure. Throwing an exception is the correct action.
                throw new IllegalStateException("Received triggerSuccessor event without an 'originatingProcessId'. Cannot proceed or send confirmation.");
            }

            logger.info("Parent process {} is creating {} successor processes as requested by child {}.",
                    process.getId(), payload.getSuccessorsToCreate().size(), originatingProcessId);

            // 1. Create the new processes and add them to the parent's transient list.
            List<Process> newSubProcesses = createProcessesFromPayloads(process.getId(), payload.getSuccessorsToCreate());
            addSubProcessesToParent(process, newSubProcesses);

            // 2. Directly send the 'successorCreated' confirmation event back to the original child.
            notifySuccessor.notifySuccessorCreated(originatingProcessId);

        } catch (Exception e) {
            // 3. If any part of the process fails, send a failure notification instead.
            logger.error("Failed to create successor processes for parentId {} as requested by child {}.",
                    process.getId(), originatingProcessId, e);

            if (originatingProcessId != null) {
                SuccessorFailedPayload failedPayload = new SuccessorFailedPayload();
                failedPayload.setExceptionMessage("Parent process " + process.getId() + " failed to create successor: " + e.getMessage());
                failedPayload.setStackTrace(ExceptionUtils.getStackTrace(e));
                notifySuccessor.notifySuccessorCreationFailed(originatingProcessId, process.getId(), e);
            }
        }
    }
}