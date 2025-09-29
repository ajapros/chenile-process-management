package org.chenile.orchestrator.process.model.payload;

import org.chenile.orchestrator.process.model.payload.common.SubProcessPayload;
import org.chenile.workflow.param.MinimalPayload;

import java.util.List;

/**
    Customized Payload for the chunkIt event.
*/
public class StartProcessingPayload extends MinimalPayload{
    public List<SubProcessPayload> subProcesses;
    public boolean splitCompleted = false;

    public List<SubProcessPayload> getSubProcesses() {
        return subProcesses;
    }

    public void setSubProcesses(List<SubProcessPayload> subProcesses) {
        this.subProcesses = subProcesses;
    }

    public boolean isSplitCompleted() {
        return splitCompleted;
    }

    public void setSplitCompleted(boolean splitCompleted) {
        this.splitCompleted = splitCompleted;
    }
}
