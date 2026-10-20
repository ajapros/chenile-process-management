package org.chenile.orchestrator.process.model.payload;

import java.util.ArrayList;
import java.util.List;
import org.chenile.workflow.param.MinimalPayload;

public class ErrorPayload extends MinimalPayload {
  public List<String> errors = new ArrayList<>();
  public String exceptionMessage;
  public String stackTrace;
}
