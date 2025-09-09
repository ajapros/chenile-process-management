package org.chenile.orchestrator.process.model.payload.splitting;

import org.chenile.orchestrator.process.model.payload.common.ProcessCreationPayload;
import org.chenile.workflow.param.MinimalPayload;

import java.util.ArrayList;
import java.util.List;

public class SplitCompletedPayload extends MinimalPayload {
    private List<ProcessCreationPayload> subProcesses = new ArrayList<>();

    public List<ProcessCreationPayload> getSubProcesses() {
        return subProcesses;
    }

    public void setSubProcesses(List<ProcessCreationPayload> subProcesses) {
        this.subProcesses = subProcesses;
    }
}