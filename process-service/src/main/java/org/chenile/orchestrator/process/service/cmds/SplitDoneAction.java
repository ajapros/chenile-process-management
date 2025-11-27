package org.chenile.orchestrator.process.service.cmds;

import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.StartProcessingPayload;
import org.chenile.orchestrator.process.model.payload.SubProcessPayload;
import org.chenile.orchestrator.process.service.defs.ProcessConfigurator;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.chenile.workflow.service.stmcmds.AbstractSTMTransitionAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 Contains customized logic for the transition. Common logic resides at {@link DefaultSTMTransitionAction}
 <p>Use this class if you want to augment the common logic for this specific transition</p>
 <p>Use a customized payload if required instead of MinimalPayload</p>
*/
public class SplitDoneAction extends AbstractSTMTransitionAction<Process,
		StartProcessingPayload>{
	Logger logger = LoggerFactory.getLogger(this.getClass());
	@Autowired
	ProcessConfigurator processConfigurator;
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
		logger.info("===AAA=== list.size() = {} Creating {} ",
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
			subProcess.input = p.args;
			subProcess.leaf = p.leaf;
			addSuccessors(subProcess,list);
			list.add(subProcess);
		}
		return list;
	}

	private void addSuccessors(Process subProcess, List<Process> list) {
		ProcessDef childProcessDef = processConfigurator.processes.processMap.get(subProcess.processType);
		if (childProcessDef == null || childProcessDef.successors == null ||
				childProcessDef.successors.isEmpty()) {
			return;
		}
		// Ensure that the sub process has an ID. Else, it is not possible to track the successors.
		if (subProcess.id == null || subProcess.id.isEmpty()){
			subProcess.id = String.valueOf(UUID.randomUUID());
		}
		int index = 1;
		for (String successor: childProcessDef.successors) {

			Process successorProcess = new Process();
			successorProcess.id = subProcess.id + successor;
			logger.info("Creating Successor for process ID = " + subProcess.id +
					" Processing successor type = " + successor + " successor process ID is " +
					successorProcess.id);
			successorProcess.processType = successor;
			ProcessDef successorProcessDef = processConfigurator.processes.processMap.get(successor);
			if (successorProcessDef == null){
				logger.warn("Not starting successor " + successor + " since its ProcessDef not configured");
				continue;
			}
			successorProcess.leaf = successorProcessDef.leaf;
			successorProcess.input = subProcess.input;
			successorProcess.dormant = true;
			successorProcess.parentId = subProcess.parentId;
			successorProcess.predecessorId = subProcess.id;
			list.add(successorProcess);
		}
	}

}
