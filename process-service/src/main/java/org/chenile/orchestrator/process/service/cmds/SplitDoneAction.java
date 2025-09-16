package org.chenile.orchestrator.process.service.cmds;

import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.StartProcessingPayload;
import org.chenile.orchestrator.process.model.SubProcessPayload;
import org.chenile.orchestrator.process.service.defs.ProcessConfigurator;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.chenile.workflow.api.StateEntityService;
import org.chenile.workflow.service.stmcmds.AbstractSTMTransitionAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

/**
 Contains customized logic for the transition. Common logic resides at {@link DefaultSTMTransitionAction}
 <p>Use this class if you want to augment the common logic for this specific transition</p>
 <p>Use a customized payload if required instead of MinimalPayload</p>
*/
public class SplitDoneAction extends AbstractSTMTransitionAction<Process,
        StartProcessingPayload>{
	Logger logger = LoggerFactory.getLogger(this.getClass());
	@Autowired
	StateEntityService<Process> processService;
	@Autowired
	ProcessConfigurator processConfigurator;
	@Override
	public void transitionTo(Process process,
							 StartProcessingPayload payload,
							 State startState, String eventId,
							 State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {
		List<Process> list = makeSubProcesses(process,payload);
		process.subProcesses.addAll(list);
		process.numSubProcesses = process.subProcesses.size();
	}

	private List<Process> makeSubProcesses(Process process,StartProcessingPayload payload) {
		if (payload.subProcesses == null || payload.subProcesses.isEmpty()) return null;
		List<Process> list = new ArrayList<>();
		for (SubProcessPayload p: payload.subProcesses) {
			Process subProcess = new Process();
			if(p.childId != null) subProcess.id = p.childId;
			if(p.processType != null)subProcess.processType = p.processType;
			subProcess.parentId = process.id;
			subProcess.args = p.args;
			subProcess.leaf = p.leaf;
			addSuccessor(subProcess,list);
			list.add(subProcess);
		}
		return list;
	}

	private void addSuccessor(Process subProcess, List<Process> list) {
		ProcessDef childProcessDef = processConfigurator.processes.processMap.get(subProcess.processType);
		if (childProcessDef == null || childProcessDef.successors == null ||
				childProcessDef.successors.isEmpty()) {
			return;
		}
		for (String successor: childProcessDef.successors) {
			logger.info("Starting Successor for process type = " + childProcessDef.processType +
					" Processing successor type = " + successor);
			Process successorProcess = new Process();
			successorProcess.processType = successor;
			ProcessDef successorProcessDef = processConfigurator.processes.processMap.get(successor);
			if (successorProcessDef == null){
				logger.warn("Not starting successor " + successor + " since its ProcessDef not configured");
				continue;
			}
			successorProcess.leaf = successorProcessDef.leaf;
			successorProcess.args = subProcess.args;
			successorProcess.dormant = true;
			successorProcess.parentId = subProcess.parentId;
			successorProcess.predecessorId = subProcess.id;
			list.add(successorProcess);
		}
	}

}
