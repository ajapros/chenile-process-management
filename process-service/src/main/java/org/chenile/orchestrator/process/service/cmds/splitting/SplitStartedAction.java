package org.chenile.orchestrator.process.service.cmds.splitting;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.splitting.SplitCompletedPayload;
import org.chenile.orchestrator.process.model.payload.splitting.SplitStartedPayload;
import org.chenile.orchestrator.process.service.cmds.BaseProcessAction;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;

import java.util.List;

public class SplitStartedAction extends BaseProcessAction<SplitStartedPayload> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SplitStartedAction.class);

    @Override
    public void transitionTo(Process process,
                             SplitStartedPayload payload,
                             State startState, String eventId,
                             State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {


        logActionInvocation(process, eventId, startState, endState);
        List<Process> newSubProcesses = createProcessesFromPayloads(process.getId(), payload.getSubProcesses());
        addSubProcessesToParent(process, newSubProcesses);
        process.setSplitComplete(payload.isSplitComplete());
    }

}
