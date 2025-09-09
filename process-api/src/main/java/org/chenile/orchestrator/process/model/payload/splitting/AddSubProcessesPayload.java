package org.chenile.orchestrator.process.model.payload.splitting;

import org.chenile.orchestrator.process.model.payload.common.ProcessCreationPayload;
import org.chenile.workflow.param.MinimalPayload;

import java.util.ArrayList;
import java.util.List;

public class AddSubProcessesPayload extends MinimalPayload {
    private List<ProcessCreationPayload> subProcesses = new ArrayList<>();
    // For Last subProcess, set isSplitComplete to true or send a SplitCompletedPayload.
    private boolean splitComplete = false;

    public List<ProcessCreationPayload> getSubProcesses() {
        return subProcesses;
    }

    public void setSubProcesses(List<ProcessCreationPayload> subProcesses) {
        this.subProcesses = subProcesses;
    }

    public boolean isSplitComplete() {
        return splitComplete;
    }

    public void setSplitComplete(boolean splitComplete) {
        this.splitComplete = splitComplete;
    }
}
