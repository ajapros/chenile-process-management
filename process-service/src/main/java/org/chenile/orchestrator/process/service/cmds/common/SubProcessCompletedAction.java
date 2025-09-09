package org.chenile.orchestrator.process.service.cmds.common;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.common.SubProcessCompletedPayload;
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
public class SubProcessCompletedAction extends BaseProcessAction<SubProcessCompletedPayload> {
    private static final Logger logger = LoggerFactory.getLogger(SubProcessCompletedAction.class);

    @Override
    public void transitionTo(Process process,
                             SubProcessCompletedPayload payload,
                             State startState, String eventId,
                             State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {

        logActionInvocation(process, eventId, startState, endState);

        if (process.isAllSubProcessesCompleted()) {
            logger.warn("Ignoring late event '{}' for process {}. The process is already in state '{}' because all {} subprocesses have been completed.",
                    eventId,
                    process.getId(),
                    process.getCurrentState(),
                    process.getNumSubProcesses());
            return;
        }
        process.setNumCompletedSubProcesses(process.getNumCompletedSubProcesses() + 1);
        logger.info("Process {} completion count updated to {}/{}",
                process.getId(), process.getNumCompletedSubProcesses(), process.getNumSubProcesses());
    }

}
