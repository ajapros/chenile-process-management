package org.chenile.orchestrator.process.service.cmds;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.DoneSuccessfullyPayload;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.chenile.workflow.service.stmcmds.AbstractSTMTransitionAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * If subprocess is done successfully, then we should increment the num of completed sub processes.
 * Completion is reported to the parent so it can continue aggregation. Any
 * follow-on process is now an application-defined subscriber to ProcessCompleted.
*/
public class SubProcessDoneSuccessfullyAction extends AbstractSTMTransitionAction<Process,
		DoneSuccessfullyPayload>{
	Logger logger = LoggerFactory.getLogger(this.getClass());

	@Override
	public void transitionTo(Process process,
							 DoneSuccessfullyPayload payload,
							 State startState, String eventId,
							 State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {
		if (process.numCompletedSubProcesses == process.numSubProcesses) {
			logger.error("Received the sub process Processing event when the numCompletedSubProcesses = numSubProcesses ("+ process.numSubProcesses + ")");
			return; // discard this event
		}
		process.numCompletedSubProcesses++;
	}
}
