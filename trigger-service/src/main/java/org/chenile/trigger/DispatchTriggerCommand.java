package org.chenile.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.core.event.EventProcessor;
import org.chenile.trigger.model.Trigger;
import org.chenile.orchestrator.process.outbox.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/** A trigger is a durable event invocation, independent of processes and cron. */
public class DispatchTriggerCommand implements OutboxCommandLifecycle<OutboxCommand> {
    public static final String TYPE = "DISPATCH_TRIGGER";
    private static final Logger LOG = LoggerFactory.getLogger(DispatchTriggerCommand.class);
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
    @Override public Map<String, String> executionHeaders(OutboxCommand command) throws Exception {
        Trigger trigger = mapper.readValue(command.payload, Trigger.class);
        var headers = new HashMap<>(trigger.headers());
        headers.put("x-chenile-trigger-id", command.id);
        headers.put("x-chenile-tenant-id", command.tenantId);
        return headers;
    }
    @Override public void dispatch(OutboxCommand command) throws Exception {
        Trigger trigger = mapper.readValue(command.payload, Trigger.class);
        var exchanges = events.handleEvent(trigger.eventId(), trigger.payload(), executionHeaders(command));
        if (exchanges.isEmpty()) throw new IllegalStateException("No event subscriber configured for " + trigger.eventId());
        for (var exchange : exchanges) {
            if (exchange.getException() != null)
                throw new IllegalStateException("Trigger subscriber failed for " + command.id, exchange.getException());
            logResponse(command.id, trigger.eventId(), exchange.getResponse());
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
