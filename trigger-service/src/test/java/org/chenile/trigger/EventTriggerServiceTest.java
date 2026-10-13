package org.chenile.trigger;

import org.chenile.core.event.EventProcessor;
import org.chenile.core.model.ChenileConfiguration;
import org.chenile.core.model.ChenileEventDefinition;
import org.chenile.trigger.store.TriggerLogStore;
import org.chenile.trigger.model.TriggerLog;
import org.chenile.trigger.model.TriggerLogStatus;
import org.chenile.trigger.model.TriggerInput;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventTriggerServiceTest {
    @Test
    void dispatchesTheGivenEventUsingItsDeclaredPayloadTypeOnlyOnce() {
        ChenileConfiguration configuration = new ChenileConfiguration("test", null);
        ChenileEventDefinition definition = new ChenileEventDefinition();
        definition.setId("CustomerEvent");
        definition.setType(CustomerEvent.class);
        configuration.addEvent(definition);
        InMemoryTriggerLogStore logger = new InMemoryTriggerLogStore();
        TriggerObserver observer = mock(TriggerObserver.class);
        EventProcessor processor = mock(EventProcessor.class);
        when(processor.handleEvent(eq("CustomerEvent"), any(), anyMap())).thenReturn(List.of());
        EventTriggerService service = new EventTriggerService(logger, processor, configuration, observer);
        TriggerInput input = new TriggerInput();
        input.triggerId = "trigger-1";
        input.eventId = "CustomerEvent";
        input.headers.put("source", "HTTP");
        input.payload = Map.of("customerId", "customer-7", "triggerId", "trigger-1");

        assertTrue(service.trigger(input).dispatched());
        assertTrue(service.trigger(input).duplicate());
        var payload = org.mockito.ArgumentCaptor.forClass(CustomerEvent.class);
        @SuppressWarnings("unchecked")
        var headers = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(processor, times(1)).handleEvent(eq("CustomerEvent"), payload.capture(), headers.capture());
        assertEquals("customer-7", payload.getValue().customerId);
        assertEquals("trigger-1", payload.getValue().triggerId);
        assertEquals("trigger-1", headers.getValue().get("x-chenile-trigger-id"));
        assertEquals("HTTP", headers.getValue().get("source"));
        assertFalse(input.headers.containsKey("x-chenile-trigger-id"));
        assertEquals(TriggerLogStatus.COMPLETED, logger.logs.get(0).status);
        verify(observer, times(1)).started("log-0", input);
        verify(observer, times(1)).finished("log-0", null);
    }

    @Test
    void failedDispatchRetainsTheClaimAndSuppressesAnotherAttempt() {
        InMemoryTriggerLogStore store = new InMemoryTriggerLogStore();
        TriggerObserver observer = mock(TriggerObserver.class);
        EventProcessor processor = mock(EventProcessor.class);
        when(processor.handleEvent(anyString(), any(), anyMap()))
                .thenThrow(new IllegalStateException("subscriber failed"));
        EventTriggerService service = new EventTriggerService(store, processor,
                new ChenileConfiguration("test", null), observer);
        TriggerInput input = new TriggerInput();
        input.triggerId = "failed-trigger";
        input.eventId = "CustomerEvent";

        assertThrows(IllegalStateException.class, () -> service.trigger(input));
        assertEquals(TriggerLogStatus.FAILED, store.logs.get(0).status);
        var duplicate = service.trigger(input);
        assertTrue(duplicate.duplicate());
        assertFalse(duplicate.dispatched());
        verify(processor, times(1)).handleEvent(anyString(), any(), anyMap());
        verify(observer, times(1)).started("log-0", input);
        verify(observer, times(1)).finished(eq("log-0"), isA(IllegalStateException.class));
    }

    @Test
    void aConcurrentClaimPreventsTheLosingDispatch() {
        TriggerLogStore store = mock(TriggerLogStore.class);
        TriggerLog winner = new TriggerLog();
        winner.id = "winning-claim";
        winner.status = TriggerLogStatus.RECEIVED;
        when(store.findByTriggerIdAndEventName("trigger-1", "CustomerEvent"))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(store.record(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));
        EventProcessor processor = mock(EventProcessor.class);
        EventTriggerService service = new EventTriggerService(store, processor,
                new ChenileConfiguration("test", null));
        TriggerInput input = new TriggerInput();
        input.triggerId = "trigger-1";
        input.eventId = "CustomerEvent";

        var result = service.trigger(input);
        assertTrue(result.duplicate());
        assertFalse(result.dispatched());
        assertEquals("winning-claim", result.logId());
        verifyNoInteractions(processor);
    }

    static class CustomerEvent { public String customerId; public String triggerId; }

    static class InMemoryTriggerLogStore implements TriggerLogStore {
        final List<TriggerLog> logs = new ArrayList<>();
        @Override public TriggerLog record(TriggerLog log) { log.id = "log-" + logs.size(); logs.add(log); return log; }
        @Override public Optional<TriggerLog> findByTriggerIdAndEventName(String triggerId, String eventName) {
            return logs.stream().filter(l -> Objects.equals(l.triggerId, triggerId) && Objects.equals(l.eventName, eventName)).findFirst();
        }
        @Override public TriggerLog markCompleted(String id) { TriggerLog log = find(id); log.status = TriggerLogStatus.COMPLETED; return log; }
        @Override public TriggerLog markFailed(String id) { TriggerLog log = find(id); log.status = TriggerLogStatus.FAILED; return log; }
        private TriggerLog find(String id) { return logs.stream().filter(l -> l.id.equals(id)).findFirst().orElseThrow(); }
    }
}
