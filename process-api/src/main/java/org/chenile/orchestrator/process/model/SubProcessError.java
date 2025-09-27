package org.chenile.orchestrator.process.model;

import jakarta.persistence.*;
import org.chenile.jpautils.entity.BaseJpaEntity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "subprocess_error")
public class SubProcessError extends BaseJpaEntity {
    private String processId;
    private Date timeOfCompletion;

    /**
     * For capturing the message from a single, critical system/technical exception
     * that caused a worker to crash.
     */
    @Column(name = "exception_message", columnDefinition = "TEXT")
    private String exceptionMessage;

    /**
     * For capturing the stack trace from a critical exception for debugging.
     */
    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

/*    *//**
     * For collecting multiple, specific business or data validation errors.
     *//*
    @ElementCollection
    @CollectionTable(name = "subprocess_error_errors", joinColumns = @JoinColumn(name = "subprocess_error_id"))
    @Column(name = "errors", columnDefinition = "TEXT")
    private List<String> errors = new ArrayList<>();*/

    @ElementCollection
    @CollectionTable(name = "subprocess_error_validation_errors", joinColumns = @JoinColumn(name = "subprocess_error_id"))
    @Column(name = "validation_error", columnDefinition = "TEXT")
    private List<String> validationErrors = new ArrayList<>();

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public Date getTimeOfCompletion() {
        return timeOfCompletion;
    }

    public void setTimeOfCompletion(Date timeOfCompletion) {
        this.timeOfCompletion = timeOfCompletion;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<String> validationErrors) {
        this.validationErrors = validationErrors != null ? new ArrayList<>(validationErrors) : new ArrayList<>();
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


    @Override
    public SubProcessError clone() {
        SubProcessError clone = new SubProcessError();
        clone.setProcessId(this.processId);
        clone.setTimeOfCompletion(this.timeOfCompletion);
        clone.setValidationErrors(this.validationErrors);
        clone.setExceptionMessage(this.exceptionMessage);
        clone.setStackTrace(this.stackTrace);
        return clone;
    }
}