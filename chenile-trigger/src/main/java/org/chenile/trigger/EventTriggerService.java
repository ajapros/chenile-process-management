package org.chenile.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.core.context.ChenileExchange;
import org.chenile.core.event.EventProcessor;
import org.chenile.core.model.ChenileConfiguration;
import org.chenile.core.model.ChenileEventDefinition;
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
    private final TriggerLogStore triggerLogStore;
    private final EventProcessor eventProcessor;
    private final ChenileConfiguration configuration;
    private final ObjectMapper objectMapper;
    private TriggerObserver observer = TriggerObserver.NONE;

    public EventTriggerService(TriggerLogStore store, EventProcessor processor,
                               ChenileConfiguration configuration, TriggerObserver observer) {
        this(store, processor, configuration);
        this.observer = observer;
    }

    public EventTriggerService(TriggerLogStore triggerLogStore, EventProcessor eventProcessor,
                               ChenileConfiguration configuration) {
        this(triggerLogStore, eventProcessor, configuration, new ObjectMapper());
    }

    EventTriggerService(TriggerLogStore triggerLogStore, EventProcessor eventProcessor,
                        ChenileConfiguration configuration, ObjectMapper objectMapper) {
        this.triggerLogStore = triggerLogStore;
        this.eventProcessor = eventProcessor;
        this.configuration = configuration;
        this.objectMapper = objectMapper;
    }

    @Override
    public TriggerResult trigger(TriggerInput input) {
        require(input != null, "Trigger input is required");
        require(input.eventId != null && !input.eventId.isBlank(), "Trigger eventId is required");
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
            observer.started(log.id, input);
            Map<String, String> headers = new HashMap<>(safeHeaders(input.headers));
            headers.put("x-chenile-trigger-id", input.triggerId);
            List<ChenileExchange> exchanges = eventProcessor.handleEvent(input.eventId, typedPayload(input), headers);
            Optional<ChenileExchange> failed = exchanges.stream().filter(e -> e.getException() != null).findFirst();
            if (failed.isPresent()) throw new IllegalStateException(failed.get().getException().getMessage(), failed.get().getException());
            triggerLogStore.markCompleted(log.id);
            observer.finished(log.id, null);
            return new TriggerResult(input.triggerId, input.eventId, log.id, true, false);
        } catch (RuntimeException exception) {
            triggerLogStore.markFailed(log.id);
            try { observer.finished(log.id, exception); }
            catch (RuntimeException historyFailure) { exception.addSuppressed(historyFailure); }
            throw exception;
        }
    }

    private TriggerResult result(TriggerInput input, TriggerLog log, boolean duplicate) {
        return new TriggerResult(input.triggerId, input.eventId, log.id, log.status == TriggerLogStatus.COMPLETED, duplicate);
    }

    private Object typedPayload(TriggerInput input) {
        ChenileEventDefinition event = configuration.getEvents().get(input.eventId);
        if (event == null || event.getType() == null || input.payload == null || event.getType().isInstance(input.payload)) return input.payload;
        return objectMapper.convertValue(input.payload, event.getType());
    }

    private TriggerLog receivedLog(TriggerInput input) {
        TriggerLog log = new TriggerLog();
        log.status = TriggerLogStatus.RECEIVED;
        log.triggerId = input.triggerId;
        log.eventName = input.eventId;
        log.triggerTime = input.triggerTime;
        return triggerLogStore.record(log);
    }

    private Map<String, String> safeHeaders(Map<String, String> headers) { return headers == null ? Map.of() : headers; }
    private void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
}
