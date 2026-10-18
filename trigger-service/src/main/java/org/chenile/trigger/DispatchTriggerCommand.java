package org.chenile.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.core.context.ContextContainer;
import org.chenile.core.event.EventProcessor;
import org.chenile.orchestrator.process.outbox.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** A trigger is a durable event invocation, independent of processes and cron. */
public class DispatchTriggerCommand implements OutboxCommandLifecycle<OutboxCommand> {
    public static final String TYPE = "DISPATCH_TRIGGER";
    private static final Logger LOG = LoggerFactory.getLogger(DispatchTriggerCommand.class);
    public record Data(String eventId, Instant triggerTime, Map<String, String> headers,
            Map<String, Object> triggerContext, Object payload) { }
    private final ProcessOutboxRepository repository;
    private final EventProcessor events;
    private final ObjectMapper mapper;
    public DispatchTriggerCommand(ProcessOutboxRepository repository, EventProcessor events, ObjectMapper mapper) {
        this.repository = repository; this.events = events; this.mapper = mapper;
    }
    @Override public String type() { return TYPE; }
    @Override public boolean shouldEnqueue(OutboxCommand command) {
        return command != null && TYPE.equals(command.commandType);
    }
    @Override public void enqueue(OutboxCommand command) { repository.enqueue(command); }
    @Override public void dispatch(OutboxCommand command) throws Exception {
        Data data = mapper.readValue(command.payload, Data.class);
        var headers = new HashMap<>(data.headers());
        headers.put("x-chenile-trigger-id", command.id);
        var context = ContextContainer.getInstance();
        var snapshot = context.snapshot();
        try {
            context.clear();
            headers.forEach(context::put);
            context.put("x-chenile-tenant-id", command.tenantId);
            var exchanges = events.handleEvent(data.eventId(), data.payload(), headers);
            if (exchanges.isEmpty()) throw new IllegalStateException("No event subscriber configured for " + data.eventId());
            for (var exchange : exchanges) {
                if (exchange.getException() != null)
                    throw new IllegalStateException("Trigger subscriber failed for " + command.id, exchange.getException());
                logResponse(command.id, data.eventId(), exchange.getResponse());
            }
        } finally {
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void beforeCommit(boolean readOnly) {
                            context.clear(); headers.forEach(context::put);
                            context.put("x-chenile-tenant-id", command.tenantId);
                        }
                        @Override public void afterCompletion(int status) { context.restore(snapshot); }
                    });
            } else context.restore(snapshot);
        }
    }

    private void logResponse(String id, String eventId, Object response) {
        try {
            String output = response instanceof String text ? text : mapper.writeValueAsString(response);
            LOG.info("Trigger {} event {} response: {}", id, eventId, output);
        } catch (Exception loggingFailure) {
            // Observation must not turn a successful invocation into a retried business effect.
            LOG.warn("Trigger {} event {} response could not be serialized for logging", id, eventId, loggingFailure);
        }
    }
}
