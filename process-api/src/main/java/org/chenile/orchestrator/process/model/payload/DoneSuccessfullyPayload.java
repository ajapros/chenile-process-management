package org.chenile.orchestrator.process.model.payload;

import org.chenile.workflow.param.MinimalPayload;

/**
    Customized Payload for the chunkProcessingDoneSuccessfully event.
*/
public class DoneSuccessfullyPayload extends MinimalPayload{
    public String predecessorId;

    public String getPredecessorId() {
        return predecessorId;
    }

    public void setPredecessorId(String predecessorId) {
        this.predecessorId = predecessorId;
    }
}
