package org.chenile.orchestrator.process.model.payload;

import jakarta.persistence.*;
import org.chenile.jpautils.entity.BaseJpaEntity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "subprocess_error")
public class SubProcessError extends BaseJpaEntity {
    public String processId;
    public Date timeOfCompletion;

    /**
     * For capturing the message from a single, critical system/technical exception
     * that caused a worker to crash.
     */
    @Column(name = "exception_message", columnDefinition = "TEXT")
    public String exceptionMessage;

    /**
     * For capturing the stack trace from a critical exception for debugging.
     */
    @Column(name = "stack_trace", columnDefinition = "TEXT")
    public String stackTrace;

    @ElementCollection
    @CollectionTable(name = "subprocess_error_validation_errors", joinColumns = @JoinColumn(name = "subprocess_error_id"))
    @Column(name = "error", columnDefinition = "TEXT")
    public List<String> errors ;

    @Override
    public SubProcessError clone() {
        SubProcessError clone = new SubProcessError();
        clone.processId = this.processId;
        clone.timeOfCompletion = this.timeOfCompletion;
        clone.errors = this.errors;
        clone.exceptionMessage = this.exceptionMessage;
        clone.stackTrace = this.stackTrace;
        return clone;
    }
}
