package org.chenile.orchestrator.process.service.cmds;

import org.chenile.orchestrator.process.model.ActivatePayload;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.chenile.workflow.service.stmcmds.AbstractSTMTransitionAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 Contains customized logic for the transition. Common logic resides at {@link DefaultSTMTransitionAction}
 <p>Use this class if you want to augment the common logic for this specific transition</p>
 <p>Use a customized payload if required instead of MinimalPayload</p>
*/
public class ActivateAction extends AbstractSTMTransitionAction<Process,
		ActivatePayload>{
	Logger logger = LoggerFactory.getLogger(this.getClass());
	@Override
	public void transitionTo(Process process,
							 ActivatePayload payload,
							 State startState, String eventId,
							 State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {
		process.dormant = false;
	}
}
