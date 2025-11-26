package org.chenile.orchestrator.process.service.cmds;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.SubProcessDoneWithErrorsPayload;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.chenile.workflow.service.stmcmds.AbstractSTMTransitionAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubProcessDoneWithErrorsAction extends BaseProcessAction<SubProcessDoneWithErrorsPayload>{
	Logger logger = LoggerFactory.getLogger(this.getClass());
	@Override
	public void transitionTo(Process process,
							 SubProcessDoneWithErrorsPayload payload,
							 State startState, String eventId,
							 State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {
		if (process.numCompletedSubProcesses == process.numSubProcesses) {
			logger.error("Received the executor done with errors event when the numCompletedSubProcesses = numSubProcesses ("+ process.numSubProcesses + ")");
			return; // discard this event
		}
		process.numCompletedSubProcesses++;
		addErrorToProcess(process, payload);
		logger.info("Process {} completion count updated to {}/{}",
				process.getId(), process.numCompletedSubProcesses, process.numSubProcesses);
	}

}
