package org.chenile.orchestrator.process.service.cmds;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.DoneWithErrorsPayload;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;

/**
 Contains customized logic for the transition. Common logic resides at {@link DefaultSTMTransitionAction}
 <p>Use this class if you want to augment the common logic for this specific transition</p>
 <p>Use a customized payload if required instead of MinimalPayload</p>
*/
public class SplitDoneWithErrorsAction extends BaseProcessAction<DoneWithErrorsPayload>{

	@Override
	public void transitionTo(Process process,
                             DoneWithErrorsPayload payload,
							 State startState, String eventId,
							 State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {
        addErrorToProcess(process, payload);
    }
}
