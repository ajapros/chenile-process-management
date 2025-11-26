package org.chenile.orchestrator.process.model.payload;

import org.chenile.workflow.param.MinimalPayload;

import java.util.ArrayList;
import java.util.List;

/**
    Customized Payload for the chunkProcessingDoneWithErrors event.
*/
public class SubProcessDoneWithErrorsPayload extends ErrorPayload{
    public String childId;
}
