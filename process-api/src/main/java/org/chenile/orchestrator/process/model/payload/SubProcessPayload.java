package org.chenile.orchestrator.process.model.payload;

import org.chenile.workflow.param.MinimalPayload;

public class SubProcessPayload extends MinimalPayload {
    public String childId; // inject the id for the child. Useful for tests
    public String args;
    public String processType;
    public boolean leaf;
}
