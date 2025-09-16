package org.chenile.orchestrator.process.service.cmds;

import org.chenile.orchestrator.process.configuration.dao.ProcessRepository;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.stm.impl.STMActionBase;
import org.chenile.workflow.activities.model.ActivityEnabledStateEntity;
import org.chenile.workflow.api.StateEntityService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class PredecessorAction extends STMActionBase<Process> {

    @Autowired
    private ProcessRepository processRepository;

    @Autowired
    private StateEntityService<Process> processService;

    @Override
    public String doExecute(Process process) throws Exception {

        List<Process> predecessorList =  processRepository.findByPredecessorId(process.id);
        for(Process predecessor: predecessorList){
            processService.processById(predecessor.getId(), Constants.ACTIVATE_DORMANT_EVENT, null);
        }
        return Constants.YES;
    }

}
