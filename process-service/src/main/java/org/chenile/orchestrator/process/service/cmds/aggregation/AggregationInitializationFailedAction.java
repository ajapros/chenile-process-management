package org.chenile.orchestrator.process.service.cmds.aggregation;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.aggregation.AggregationFailedPayload;
import org.chenile.orchestrator.process.model.payload.aggregation.AggregationInitializationFailedPayload;
import org.chenile.orchestrator.process.service.cmds.BaseProcessAction;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AggregationInitializationFailedAction extends BaseProcessAction<AggregationInitializationFailedPayload> {
    private static final Logger logger = LoggerFactory.getLogger(AggregationInitializationFailedAction.class);

    @Override
    public void transitionTo(Process process,
                             AggregationInitializationFailedPayload payload,
                             State startState, String eventId,
                             State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {

        logActionInvocation(process, eventId, startState, endState);

        addErrorToProcess(process, payload);
    }

}