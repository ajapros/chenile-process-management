package org.chenile.orchestrator.process.service.impl;

import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.service.defs.ProcessConfigurator;
import org.chenile.stm.STM;
import org.chenile.stm.impl.STMActionsInfoProvider;
import org.chenile.utils.entity.service.EntityStore;
import org.chenile.workflow.dto.StateEntityServiceResponse;
import org.chenile.workflow.service.impl.StateEntityServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;

public class ProcessManager extends StateEntityServiceImpl<Process> {
    @Autowired
    ProcessConfigurator processConfigurator;
    /**
     * @param stm                    the state machine that has read the corresponding State Transition Diagram
     * @param stmActionsInfoProvider the provider that gives out info about the state diagram
     * @param entityStore            the store for persisting the entity
     */
    public ProcessManager(STM<Process> stm, STMActionsInfoProvider stmActionsInfoProvider, EntityStore<Process> entityStore) {
        super(stm, stmActionsInfoProvider, entityStore);
    }

    @Override
    public StateEntityServiceResponse<Process> create(Process process) {
        makeProcessLeafIfConfigured(process);
        return super.create(process);
    }

    private void makeProcessLeafIfConfigured(Process process) {
        if (process == null || process.isLeaf() || process.getProcessType() == null) return;

        ProcessDef processDef = processConfigurator.getProcessDef(process.getProcessType());
        if (processDef == null) return;

        process.setLeaf(processDef.isLeaf());
    }
}
