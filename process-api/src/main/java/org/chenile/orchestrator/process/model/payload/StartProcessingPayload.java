package org.chenile.orchestrator.process.model.payload;

import java.util.List;
import org.chenile.workflow.param.MinimalPayload;

/** Customized Payload for the chunkIt event. */
public class StartProcessingPayload extends MinimalPayload {
  public List<SubProcessPayload> subProcesses;
}
