package org.chenile.orchestrator.process.config.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Information about a process type.
 * This is optional but highly desirable to give.
 */
public class ProcessDef {
    public String parentProcessType;
    /** Start this process when a process of this type completes. */
    public String predecessorProcessType;
    /** Select predecessor input, output, or both; omitted values default to BOTH. */
    public PredecessorArgs predecessorArgs = PredecessorArgs.BOTH;
    public String processType;
    public String args;
    public boolean leaf;

    /** Configuration passed to every splitter, executor, and aggregator for this process type. */
    public Map<String,String> config = new HashMap<>();
}
