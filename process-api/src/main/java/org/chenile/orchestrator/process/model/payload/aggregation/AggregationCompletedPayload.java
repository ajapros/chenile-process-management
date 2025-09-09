package org.chenile.orchestrator.process.model.payload.aggregation;

import org.chenile.orchestrator.process.model.payload.common.ProcessCreationPayload;
import org.chenile.workflow.param.MinimalPayload;

import java.util.ArrayList;
import java.util.List;

public class AggregationCompletedPayload extends MinimalPayload {
    private List<ProcessCreationPayload> successorsToCreate = new ArrayList<>();

    public List<ProcessCreationPayload> getSuccessorsToCreate() {
        return successorsToCreate;
    }

    public void setSuccessorsToCreate(List<ProcessCreationPayload> successorsToCreate) {
        this.successorsToCreate = successorsToCreate;
    }

    public boolean hasSuccessor() {
        return this.successorsToCreate != null && !this.successorsToCreate.isEmpty();
    }
}
