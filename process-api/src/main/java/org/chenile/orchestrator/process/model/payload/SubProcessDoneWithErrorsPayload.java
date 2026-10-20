package org.chenile.orchestrator.process.model.payload;

/** Customized Payload for the chunkProcessingDoneWithErrors event. */
public class SubProcessDoneWithErrorsPayload extends ErrorPayload {
  public String childId;
}
