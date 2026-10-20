package org.chenile.orchestrator.process.config.reader;

import java.util.List;
import org.chenile.orchestrator.process.config.model.ProcessDef;

public interface IProcessConfigurator {
  public ProcessDef findByName(String name);

  List<ProcessDef> findByPredecessorProcessType(String processType);
}
