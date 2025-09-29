package org.chenile.orchestrator.process.service.cmds;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.DoneWithErrorsPayload;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 This is called if this process is done with errors.
*/
public class DoneWithErrorsAction extends BaseProcessAction<DoneWithErrorsPayload>{
	Logger logger = LoggerFactory.getLogger(this.getClass());
	@Override
	public void transitionTo(Process process,
							 DoneWithErrorsPayload payload,
							 State startState, String eventId,
							 State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {
		if ( process.numSubProcesses > 0) {
			logger.error("Received DoneWithErrors for a non leaf process " + process.id);
			return; // discard this event
		}

        addErrorToProcess(process, payload);

        /*if (payload.errors != null){
			SubProcessError subProcessError = new SubProcessError();
			subProcessError.processId = process.id;
			subProcessError.timeOfCompletion = new Date();
			subProcessError.errors = payload.errors;
			process.errors.add(subProcessError);
		}*/
	}

}
