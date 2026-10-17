package org.chenile.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.core.context.HeaderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.chenile.core.context.ChenileExchange;
import org.chenile.core.event.EventProcessor;
import org.chenile.trigger.store.TriggerLogStore;
import org.chenile.trigger.model.TriggerLog;
import org.chenile.trigger.model.TriggerLogStatus;
import org.chenile.trigger.model.TriggerInput;
import org.chenile.trigger.model.TriggerResult;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Dispatches a fully-formed trigger event through Chenile's existing EventProcessor. */
public class EventTriggerService implements TriggerService {
    private static final Logger LOG = LoggerFactory.getLogger(EventTriggerService.class);
    private final ObjectMapper historyMapper = new ObjectMapper().findAndRegisterModules();
    private final TriggerLogStore triggerLogStore;
    private final EventProcessor eventProcessor;
    private TriggerObserver observer = TriggerObserver.NONE;

    public EventTriggerService(TriggerLogStore store, EventProcessor processor, TriggerObserver observer) {
        this(store, processor);
        this.observer = observer;
    }

    public EventTriggerService(TriggerLogStore triggerLogStore, EventProcessor eventProcessor) {
        this.triggerLogStore = triggerLogStore;
        this.eventProcessor = eventProcessor;
    }

    @Override
    public TriggerResult trigger(TriggerInput input) {
        require(input != null, "Trigger input is required");
        require(input.eventId != null && !input.eventId.isBlank(), "Trigger eventId is required");
        for (String header : safeHeaders(input.headers).keySet())
            require(header != null && header.toLowerCase(java.util.Locale.ROOT).startsWith("x-"),
                    "Trigger headers must start with x-");
        if (input.triggerId == null || input.triggerId.isBlank()) input.triggerId = UUID.randomUUID().toString();
        if (input.triggerTime == null) input.triggerTime = Instant.now();
        Optional<TriggerLog> existing = triggerLogStore.findByTriggerIdAndEventName(input.triggerId, input.eventId);
        if (existing.isPresent()) return result(input, existing.get(), true);

        TriggerLog log;
        try {
            log = receivedLog(input);
        } catch (DataIntegrityViolationException duplicate) {
            return result(input, triggerLogStore.findByTriggerIdAndEventName(input.triggerId, input.eventId)
                    .orElseThrow(() -> duplicate), true);
        }
        try {
            triggerLogStore.markRunning(log.id);
            notifyStarted(log.id, input);
            Map<String, String> headers = new HashMap<>(safeHeaders(input.headers));
            headers.put("x-chenile-trigger-id", input.triggerId);
            List<ChenileExchange> exchanges = eventProcessor.handleEvent(input.eventId, input.payload, headers);
            Optional<ChenileExchange> failed = exchanges.stream().filter(e -> e.getException() != null).findFirst();
            if (failed.isPresent()) throw new IllegalStateException(failed.get().getException().getMessage(), failed.get().getException());
            triggerLogStore.markCompleted(log.id);
            notifyFinished(log.id, null);
            return new TriggerResult(input.triggerId, input.eventId, log.id, true, false);
        } catch (RuntimeException exception) {
            try { triggerLogStore.markFailed(log.id, exception); }
            catch (RuntimeException persistenceFailure) { exception.addSuppressed(persistenceFailure); }
            notifyFinished(log.id, exception);
            throw exception;
        }
    }

    private TriggerResult result(TriggerInput input, TriggerLog log, boolean duplicate) {
        return new TriggerResult(input.triggerId, input.eventId, log.id, log.status == TriggerLogStatus.COMPLETED, duplicate);
    }

    private TriggerLog receivedLog(TriggerInput input) {
        TriggerLog log = new TriggerLog();
        log.status = TriggerLogStatus.RECEIVED;
        log.triggerId = input.triggerId;
        log.eventName = input.eventId;
        log.triggerTime = input.triggerTime;
        var headers = safeHeaders(input.headers);
        log.tenant = headers.get(HeaderUtils.TENANT_ID_KEY);
        log.source = headers.getOrDefault("x-chenile-trigger-source", "EXTERNAL");
        try {
            log.payload = input.payload instanceof String text ? text : historyMapper.writeValueAsString(input.payload);
            log.triggerContext = historyMapper.writeValueAsString(input.triggerContext);
        }
        catch (Exception failure) { throw new IllegalArgumentException("Cannot serialize trigger payload or context", failure); }
        return triggerLogStore.record(log);
    }

    private void notifyStarted(String id, TriggerInput input) {
        try { observer.started(id, input); }
        catch (RuntimeException failure) { LOG.warn("Trigger observer start failed for {}", id, failure); }
    }

    private void notifyFinished(String id, RuntimeException dispatchFailure) {
        try { observer.finished(id, dispatchFailure); }
        catch (RuntimeException failure) {
            if (dispatchFailure != null) dispatchFailure.addSuppressed(failure);
            LOG.warn("Trigger observer finish failed for {}", id, failure);
        }
    }

    private Map<String, String> safeHeaders(Map<String, String> headers) { return headers == null ? Map.of() : headers; }
    private void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
}
