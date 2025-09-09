package org.chenile.orchestrator.process.service.cmds.execution;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.execution.StatusUpdatePayload;
import org.chenile.orchestrator.process.service.cmds.BaseProcessAction;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatusUpdateAction extends BaseProcessAction<StatusUpdatePayload> {
    private static final Logger logger = LoggerFactory.getLogger(StatusUpdateAction.class);

    @Override
    public void transitionTo(Process process,
                             StatusUpdatePayload payload,
                             State startState, String eventId,
                             State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {

        logActionInvocation(process, eventId, startState, endState);

        int percent = payload.getPercentComplete();
        if (percent < 0) percent = 0;
        if (percent > 100) percent = 100;
        process.setCompletedPercent(percent);
    }
}