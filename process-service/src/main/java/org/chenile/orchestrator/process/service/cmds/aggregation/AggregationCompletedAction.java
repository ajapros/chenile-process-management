package org.chenile.orchestrator.process.service.cmds.aggregation;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.aggregation.AggregationCompletedPayload;
import org.chenile.orchestrator.process.service.cmds.BaseProcessAction;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class AggregationCompletedAction extends BaseProcessAction<AggregationCompletedPayload> {
    private static final Logger logger = LoggerFactory.getLogger(AggregationCompletedAction.class);

    @Override
    public void transitionTo(Process process,
                              AggregationCompletedPayload payload,
                              State startState, String eventId,
                              State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {

        logActionInvocation(process, eventId, startState, endState);

        if (payload == null || !payload.hasSuccessor()) {
            logger.debug("No successor plan in payload for processId: {} in state: {}. Proceeding without chaining.", process.getId(), process.getCurrentState().getStateId());
            return;
        }
        String parentIdForSuccessors = process.getParentId();
        List<Process> successorProcesses = createProcessesFromPayloads(parentIdForSuccessors, payload.getSuccessorsToCreate());
        process.setSuccessorsToCreate(successorProcesses);
    }
}