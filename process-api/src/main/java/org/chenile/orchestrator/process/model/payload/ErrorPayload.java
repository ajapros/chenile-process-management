package org.chenile.orchestrator.process.model.payload;

import org.chenile.workflow.param.MinimalPayload;

import java.util.ArrayList;
import java.util.List;

public class ErrorPayload extends MinimalPayload {
    public List<String> errors = new ArrayList<>();
    public String exceptionMessage;
    public String stackTrace;
}
