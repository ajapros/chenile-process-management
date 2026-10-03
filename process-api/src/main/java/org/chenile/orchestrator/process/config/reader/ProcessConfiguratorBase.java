package org.chenile.orchestrator.process.config.reader;

import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.config.model.Processes;
import java.util.List;

public class ProcessConfiguratorBase implements IProcessConfigurator{
    public Processes processes = new Processes();

    @Override
    public ProcessDef findByName(String name) {
        return processes.processMap.get(name);
    }

    @Override
    public List<ProcessDef> findByPredecessorProcessType(String processType) {
        if (processType == null || processType.isBlank()) return List.of();
        return processes.processMap.values().stream()
                .filter(def -> processType.equals(def.predecessorProcessType)).toList();
    }
}
