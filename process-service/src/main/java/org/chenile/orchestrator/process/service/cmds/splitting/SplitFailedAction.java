package org.chenile.orchestrator.process.service.cmds.splitting;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.splitting.SplitFailedPayload;
import org.chenile.orchestrator.process.service.cmds.BaseProcessAction;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;

public class SplitFailedAction extends BaseProcessAction<SplitFailedPayload> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SplitFailedAction.class);

    @Override
    public void transitionTo(Process process,
                             SplitFailedPayload payload,
                             State startState, String eventId,
                             State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {


        logActionInvocation(process, eventId, startState, endState);

        addErrorToProcess(process, payload);
    }

}
