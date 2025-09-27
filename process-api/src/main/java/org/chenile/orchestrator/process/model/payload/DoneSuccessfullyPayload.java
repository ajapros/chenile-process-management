package org.chenile.orchestrator.process.model.payload;

import org.chenile.workflow.param.MinimalPayload;

/**
    Customized Payload for the chunkProcessingDoneSuccessfully event.
*/
public class DoneSuccessfullyPayload extends MinimalPayload{
    public String childId;
}
