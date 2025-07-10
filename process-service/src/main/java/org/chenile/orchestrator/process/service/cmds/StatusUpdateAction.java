package org.chenile.orchestrator.process.service.cmds;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.StatusUpdatePayload;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.chenile.workflow.service.stmcmds.AbstractSTMTransitionAction;

public class StatusUpdateAction extends AbstractSTMTransitionAction<Process,
        StatusUpdatePayload> {

    @Override
    public void transitionTo(Process process,
                             StatusUpdatePayload payload,
                             State startState, String eventId,
                             State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {
        process.completedPercent = payload.percentComplete;
    }
}