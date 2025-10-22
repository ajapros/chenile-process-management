package org.chenile.orchestrator.process.model.payload;

import org.chenile.workflow.param.MinimalPayload;

public class StatusUpdatePayload extends MinimalPayload {
    public int percentComplete;

    public int getPercentComplete() {
        return percentComplete;
    }

    public void setPercentComplete(int percentComplete) {
        this.percentComplete = percentComplete;
    }
}
