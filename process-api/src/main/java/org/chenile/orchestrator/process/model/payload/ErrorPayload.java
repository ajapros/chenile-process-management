package org.chenile.orchestrator.process.model.payload;

import org.chenile.workflow.param.MinimalPayload;

import java.util.ArrayList;
import java.util.List;

public class ErrorPayload extends MinimalPayload {
    private List<String> validationErrors = new ArrayList<>();
    private String exceptionMessage;
    private String stackTrace;

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<String> validationErrors) {
        this.validationErrors = validationErrors;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public void addValidationError(String error) {
        this.validationErrors.add(error);
    }
}
