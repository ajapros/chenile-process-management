package org.chenile.orchestrator.process.config.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Information about a process type.
 * This is optional but highly desirable to give.
 */
public class ProcessDef {
    public List<String> successors = new ArrayList<>();
    public String parentProcessType;
    public String processType;
    public String args;
    public boolean leaf; // Is this leaf node - i.e. it does not have sub processes
    public Map<String,String> splitterConfig = new HashMap<>();
    public Map<String,String> aggregatorConfig = new HashMap<>();
    public Map<String,String> executorConfig = new HashMap<>();
}
