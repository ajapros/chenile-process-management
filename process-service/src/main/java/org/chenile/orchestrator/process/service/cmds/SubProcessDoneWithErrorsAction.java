package org.chenile.orchestrator.process.service.cmds;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.SubProcessDoneWithErrorsPayload;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 Contains customized logic for the transition. Common logic resides at {@link DefaultSTMTransitionAction}
 <p>Use this class if you want to augment the common logic for this specific transition</p>
 <p>Use a customized payload if required instead of MinimalPayload</p>
*/
public class SubProcessDoneWithErrorsAction extends BaseProcessAction<SubProcessDoneWithErrorsPayload>{
	Logger logger = LoggerFactory.getLogger(this.getClass());
	@Override
	public void transitionTo(Process process,
							 SubProcessDoneWithErrorsPayload payload,
							 State startState, String eventId,
							 State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {
		if (process.numCompletedSubProcesses == process.numSubProcesses) {
            logger.warn("Ignoring late event '{}' for process {}. The process is already in state '{}' because all {} subprocesses have been completed.",
                    eventId,
                    process.getId(),
                    process.getCurrentState(),
                    process.numSubProcesses);
			return; // discard this event
		}
		process.numCompletedSubProcesses++;
        addErrorToProcess(process, payload);
        logger.info("Process {} completion count updated to {}/{}",
                process.getId(), process.numCompletedSubProcesses, process.numSubProcesses);
	}

}
