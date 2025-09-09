package org.chenile.orchestrator.process.service.cmds.execution;

import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.DoneSuccessfullyPayload;
import org.chenile.orchestrator.process.model.payload.execution.ExecutionCompletedPayload;
import org.chenile.orchestrator.process.service.cmds.BaseProcessAction;
import org.chenile.orchestrator.process.service.cmds.DefaultSTMTransitionAction;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains customized logic for the transition. Common logic resides at {@link DefaultSTMTransitionAction}
 * <p>Use this class if you want to augment the common logic for this specific transition</p>
 * <p>Use a customized payload if required instead of MinimalPayload</p>
 */
public class ExecutionCompletedAction extends BaseProcessAction<ExecutionCompletedPayload> {
    Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void transitionTo(Process process,
                             ExecutionCompletedPayload payload,
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
    }
}
