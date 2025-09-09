package org.chenile.orchestrator.process.model.payload.successor;

import org.chenile.orchestrator.process.model.payload.common.ProcessCreationPayload;
import org.chenile.workflow.param.MinimalPayload;

import java.util.ArrayList;
import java.util.List;

public class TriggerSuccessorPayload extends MinimalPayload {
    private String originatingProcessId; // The ID of the child requesting the successor
    private List<ProcessCreationPayload> successorsToCreate = new ArrayList<>();

    public String getOriginatingProcessId() {
        return originatingProcessId;
    }

    public void setOriginatingProcessId(String originatingProcessId) {
        this.originatingProcessId = originatingProcessId;
    }

    public List<ProcessCreationPayload> getSuccessorsToCreate() {
        return successorsToCreate;
    }

    public void setSuccessorsToCreate(List<ProcessCreationPayload> successorsToCreate) {
        this.successorsToCreate = successorsToCreate;
    }
}
