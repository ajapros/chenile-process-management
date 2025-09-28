package org.chenile.orchestrator.process.model.payload;

import org.chenile.orchestrator.process.model.payload.common.SubProcessPayload;
import org.chenile.workflow.param.MinimalPayload;

import java.util.List;

/**
    Customized Payload for the chunkIt event.
*/
public class StartProcessingPayload extends MinimalPayload{
    public List<SubProcessPayload> subProcesses;
}
