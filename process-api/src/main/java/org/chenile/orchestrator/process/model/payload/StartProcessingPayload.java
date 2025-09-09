package org.chenile.orchestrator.process.model.payload;

import org.chenile.orchestrator.process.model.payload.common.ProcessCreationPayload;
import org.chenile.workflow.param.MinimalPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Customized Payload for the chunkIt event.
 */
public class StartProcessingPayload extends MinimalPayload {
    private List<ProcessCreationPayload> subProcesses = new ArrayList<>();

    public List<ProcessCreationPayload> getSubProcesses() {
        return Collections.unmodifiableList(subProcesses);
    }

    public void setSubProcesses(List<ProcessCreationPayload> subProcesses) {
        this.subProcesses = subProcesses != null ? new ArrayList<>(subProcesses) : new ArrayList<>();
    }
}