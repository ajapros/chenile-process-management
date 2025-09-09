package org.chenile.orchestrator.process.service.cmds.successor;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.successor.SuccessorCreatedPayload;
import org.chenile.orchestrator.process.model.payload.successor.TriggerSuccessorPayload;
import org.chenile.orchestrator.process.service.cmds.BaseProcessAction;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SuccessorCreatedAction extends BaseProcessAction<SuccessorCreatedPayload> {
    private static final Logger logger = LoggerFactory.getLogger(SuccessorCreatedAction.class);

    @Override
    public void transitionTo(Process process,
                             SuccessorCreatedPayload payload,
                             State startState, String eventId,
                             State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {

        logActionInvocation(process, eventId, startState, endState);

        // Nothing is needed 527305
    }
}