package org.chenile.orchestrator.process.service.impl;

import org.chenile.base.exception.NotFoundException;
import org.chenile.orchestrator.process.api.ProcessManager;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.configuration.dao.ProcessRepository;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.service.defs.ProcessConfigurator;
import org.chenile.stm.STM;
import org.chenile.stm.impl.STMActionsInfoProvider;
import org.chenile.utils.entity.service.EntityStore;
import org.chenile.workflow.dto.StateEntityServiceResponse;
import org.chenile.workflow.service.impl.StateEntityServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

public class ProcessManagerImpl extends StateEntityServiceImpl<Process> implements ProcessManager {
    @Autowired
    ProcessConfigurator processConfigurator;
    @Autowired
    ProcessRepository processRepository;
    public ProcessManagerImpl(STM<Process> stm, STMActionsInfoProvider stmActionsInfoProvider, EntityStore<Process> entityStore) {
        super(stm, stmActionsInfoProvider, entityStore);
    }

    @Override
    public StateEntityServiceResponse<Process> create(Process process) {
        makeProcessLeafIfConfigured(process);
        return super.create(process);
    }

    private void makeProcessLeafIfConfigured(Process process){
        if(process.leaf || process.processType == null) return;
        ProcessDef processDef = processConfigurator.processes.processMap.get(process.processType);
        if (processDef == null) return;
        process.leaf = processDef.leaf;
    }

    public List<Process> getSubProcesses(String processId,boolean recursive){
        StateEntityServiceResponse<Process> response = retrieve(processId);
        if (response == null)
            throw new NotFoundException(40001,"Missing process " + processId);
        Process process = response.getMutatedEntity();
        List<Process> childProcesses = new ArrayList<>();
        childProcesses.add(process);
        getSubProcesses(childProcesses,process,recursive);
        return childProcesses;
    }

    private void getSubProcesses(List<Process> childProcesses,Process process,boolean recursive){
        List<Process> processList =  processRepository.findByParentId(process.getId());
        if (processList == null || processList.isEmpty()) return;
        childProcesses.addAll(processList);
        if(!recursive) return;
        for (Process p: processList){
            getSubProcesses(childProcesses,p,recursive);
        }
    }
}
