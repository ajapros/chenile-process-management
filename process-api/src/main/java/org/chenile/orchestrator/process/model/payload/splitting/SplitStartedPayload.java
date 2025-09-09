package org.chenile.orchestrator.process.model.payload.splitting;

import org.chenile.orchestrator.process.model.payload.common.ProcessCreationPayload;
import org.chenile.workflow.param.MinimalPayload;

import java.util.ArrayList;
import java.util.List;

public class SplitStartedPayload extends MinimalPayload {
    private List<ProcessCreationPayload> subProcesses = new ArrayList<>();
    private boolean splitComplete = true;
   // we can send all the subprocesses in the SplitStartedPayload payload. In that case set splitComplete

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