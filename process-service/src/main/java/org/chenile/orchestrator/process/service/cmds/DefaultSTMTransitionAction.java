package org.chenile.orchestrator.process.service.cmds;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.chenile.workflow.param.MinimalPayload;
import org.chenile.workflow.service.stmcmds.AbstractSTMTransitionAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
    This class is invoked if no specific transition action is specified
    Extend this class to do generic things that are relevant for all actions in the workflow
*/

public class DefaultSTMTransitionAction<PayloadType extends MinimalPayload>
    extends AbstractSTMTransitionAction<Process, PayloadType> {
    Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void transitionTo(Process process, PayloadType payload,
                             State startState, String eventId, State endState, STMInternalTransitionInvoker<?> stm,
                             Transition transition) {
        logger.info("DEFAULT ACTION INVOKED: {} for processId={}, event='{}' (Transitioning: {} -> {})",
                this.getClass().getSimpleName(),
                process.getId(),
                eventId,
                startState != null ? startState.getStateId() : "null",
                endState != null ? endState.getStateId() : "null"
        );
    }
}