package org.chenile.orchestrator.process.service.cmds.splitting;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.splitting.AddSubProcessesPayload;
import org.chenile.orchestrator.process.service.cmds.BaseProcessAction;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class AddSubProcessesAction extends BaseProcessAction<AddSubProcessesPayload> {
    private static final Logger logger = LoggerFactory.getLogger(AddSubProcessesAction.class);

    @Override
    public void transitionTo(Process process,
                             AddSubProcessesPayload payload,
                             State startState, String eventId,
                             State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {

        logActionInvocation(process, eventId, startState, endState);

        if (process.isSplitComplete()) {
            int attemptedCount = (payload != null && payload.getSubProcesses() != null) ? payload.getSubProcesses().size() : 0;
            logger.warn("Ignoring event '{}' for process {}. A request to add {} sub-processes was received, but the split phase was already marked as complete.",
                    eventId,
                    process.getId(),
                    attemptedCount);
            return;
        }
        List<Process> newSubProcesses = createProcessesFromPayloads(process.getId(), payload.getSubProcesses());
        addSubProcessesToParent(process, newSubProcesses);
        process.setSplitComplete(payload.isSplitComplete());
    }
}
