package org.chenile.trigger.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.core.context.HeaderUtils;
import org.chenile.trigger.TriggerObserver;
import org.chenile.trigger.model.TriggerExecution;
import org.chenile.trigger.model.TriggerInput;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.time.Instant;
import java.util.Map;

public class DatabaseTriggerObserver implements TriggerObserver {
    private final TriggerExecutionRepository repository;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    public DatabaseTriggerObserver(TriggerExecutionRepository repository) { this.repository = repository; }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void started(String executionId, TriggerInput input) {
        var headers = input.headers == null ? Map.<String, String>of() : input.headers;
        TriggerExecution execution = new TriggerExecution();
        execution.id = executionId;
        execution.triggerId = input.triggerId;
        execution.eventName = input.eventId;
        execution.tenant = headers.get(HeaderUtils.TENANT_ID_KEY);
        execution.source = headers.getOrDefault("chenile-trigger-source", "EXTERNAL");
        execution.crontabId = headers.get("chenile-crontab-id");
        execution.triggerTime = input.triggerTime;
        execution.startedAt = Instant.now();
        execution.status = "RUNNING";
        try { execution.payload = mapper.writeValueAsString(input.payload); }
        catch (Exception failure) { throw new IllegalArgumentException("Cannot serialize trigger payload", failure); }
        repository.saveAndFlush(execution);
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finished(String executionId, RuntimeException failure) {
        repository.findById(executionId).ifPresent(execution -> {
            execution.finishedAt = Instant.now();
            execution.status = failure == null ? "COMPLETED" : "FAILED";
            execution.error = failure == null ? null : failure.getMessage();
            repository.saveAndFlush(execution);
        });
    }
}
