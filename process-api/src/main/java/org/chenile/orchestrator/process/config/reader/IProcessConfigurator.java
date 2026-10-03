package org.chenile.orchestrator.process.config.reader;

import org.chenile.orchestrator.process.config.model.ProcessDef;
import java.util.List;

public interface IProcessConfigurator {
    public ProcessDef findByName(String name);
    List<ProcessDef> findByPredecessorProcessType(String processType);
}
