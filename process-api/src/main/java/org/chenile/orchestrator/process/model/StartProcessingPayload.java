package org.chenile.orchestrator.process.model;

import org.chenile.workflow.param.MinimalPayload;

import java.util.List;

/**
    Customized Payload for the chunkIt event.
*/
public class StartProcessingPayload extends MinimalPayload{
    public List<SubProcessPayload> subProcesses;
}
