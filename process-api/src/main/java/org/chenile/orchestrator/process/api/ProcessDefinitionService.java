package org.chenile.orchestrator.process.api;

import org.chenile.orchestrator.process.config.model.ProcessDef;
import java.util.List;

/** Global process definitions; JSON-backed deployments expose read-only operations. */
public interface ProcessDefinitionService {
    ProcessDef create(ProcessDef definition);
    ProcessDef update(String processType, ProcessDef definition);
    ProcessDef retrieve(String processType);
    List<ProcessDef> findAll();
}
