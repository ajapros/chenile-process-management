package org.chenile.orchestrator.process.model;

import org.chenile.workflow.param.MinimalPayload;

import java.util.ArrayList;
import java.util.List;

/**
    Customized Payload for the chunkProcessingDoneWithErrors event.
*/
public class SubProcessDoneWithErrorsPayload extends MinimalPayload{
    public List<SubProcessError> errors = new ArrayList<>();
}
