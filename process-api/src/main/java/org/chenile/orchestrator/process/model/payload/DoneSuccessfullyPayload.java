package org.chenile.orchestrator.process.model.payload;

import org.chenile.workflow.param.MinimalPayload;

/**
    Customized Payload for the DoneSuccessfully event.
*/
public class DoneSuccessfullyPayload extends MinimalPayload{
    public String childId;
    public String output;
}
