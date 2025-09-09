package org.chenile.orchestrator.process.service.cmds.execution;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.execution.ExecutionFailedPayload;
import org.chenile.orchestrator.process.service.cmds.BaseProcessAction;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is called if this process is done with errors.
 */
public class ExecutionFailedAction extends BaseProcessAction<ExecutionFailedPayload> {
    Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void transitionTo(Process process,
                             ExecutionFailedPayload payload,
                             State startState, String eventId,
                             State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {

        logActionInvocation(process, eventId, startState, endState);

        if (process.getNumSubProcesses() > 0) {
            logger.warn("Ignoring invalid event '{}' for process {}. This event is only valid for leaf processes, but this is a non-leaf process in state '{}'.",
                    eventId,
                    process.getId(),
                    process.getCurrentState().getStateId());
            return; // discard this event
        }

        addErrorToProcess(process, payload);

    }

}
