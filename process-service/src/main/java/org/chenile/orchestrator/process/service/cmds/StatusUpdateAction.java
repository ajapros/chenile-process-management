package org.chenile.orchestrator.process.service.cmds;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.StatusUpdatePayload;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;

public class StatusUpdateAction extends BaseProcessAction<StatusUpdatePayload> {

    @Override
    public void transitionTo(Process process,
                             StatusUpdatePayload payload,
                             State startState, String eventId,
                             State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {

        int percent = payload.percentComplete;
        if (percent < 0) percent = 0;
        if (percent > 100) percent = 100;
        process.completedPercent = percent;
       // process.skipPostWorkerCreation = true;
    }
}