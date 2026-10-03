package org.chenile.orchestrator.process.service.cmds;

import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.StartProcessingPayload;
import org.chenile.orchestrator.process.model.payload.SubProcessPayload;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.chenile.workflow.service.stmcmds.AbstractSTMTransitionAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SplitDoneAction handles both splitPartiallyDone and splitDone events. This initializes a transient
 * variable {@link Process#subProcesses} to the processes that need to be created. This is used in the
 * post save hook to start all the sub process workers.
 *
*/
public class SplitDoneAction extends AbstractSTMTransitionAction<Process,
		StartProcessingPayload>{
	Logger logger = LoggerFactory.getLogger(this.getClass());
	@Override
	public void transitionTo(Process process,
							 StartProcessingPayload payload,
							 State startState, String eventId,
							 State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {
		if (eventId.equals(Constants.Events.SPLIT_DONE))
			process.splitCompleted = true;
		List<Process> list = makeSubProcesses(process,payload);
		process.subProcesses = list;
		process.numSubProcesses += list.size();
		logger.debug("list.size() = {} Creating {} ",
				list.size(), list.stream().map(p -> p.input).collect(Collectors.toList()));
	}

	private List<Process> makeSubProcesses(Process process,StartProcessingPayload payload) {
		List<Process> list = new ArrayList<>();
		if (payload.subProcesses == null || payload.subProcesses.isEmpty()) return list;
		for (SubProcessPayload p: payload.subProcesses) {
			Process subProcess = new Process();
			if(p.childId != null) subProcess.id = p.childId;
			if(p.processType != null)subProcess.processType = p.processType;
			subProcess.parentId = process.id;
			subProcess.triggerId = process.triggerId;
			subProcess.input = p.args;
			subProcess.leaf = p.leaf;
			list.add(subProcess);
		}
		return list;
	}

}
