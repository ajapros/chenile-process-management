package org.chenile.trigger;

import org.chenile.core.event.EventProcessor;
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
    @Test void persistsLifecycleAndMetadataWithoutAnObserver() {
        var store = new InMemoryTriggerLogStore();
        var processor = mock(EventProcessor.class);
        TriggerInput input = new TriggerInput();
        input.triggerId = "metadata"; input.eventId = "CustomerEvent";
        input.headers.put("x-chenile-tenant-id", "alpha");
        input.headers.put("x-chenile-trigger-source", "CRON");
        input.headers.put("x-chenile-crontab-id", "daily");
        input.triggerContext.putAll(input.headers);
        input.triggerContext.put("adapterDetail", Map.of("attempt", 1));
        input.payload = "  {\"customerId\":\"one\"}  ";
        when(processor.handleEvent(anyString(), any(), anyMap())).thenAnswer(call -> {
            var record = store.logs.get(0);
            assertEquals(TriggerLogStatus.RUNNING, record.status);
            assertNotNull(record.startedAt);
            assertNull(record.finishedAt);
            assertEquals("alpha", record.tenant);
            assertEquals("CRON", record.source);
            assertEquals(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(input.triggerContext),
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(record.triggerContext));
            assertEquals(input.payload, record.payload);
            assertSame(input.payload, call.getArgument(1));
            return List.of();
        });
        var service = new EventTriggerService(store, processor);
        assertTrue(service.trigger(input).dispatched());
        assertEquals(1, store.logs.size());
        assertEquals(TriggerLogStatus.COMPLETED, store.logs.get(0).status);
        assertNotNull(store.logs.get(0).finishedAt);
    }

    @Test void observerFailuresCannotChangeThePersistedDispatchOutcome() {
        var store = new InMemoryTriggerLogStore();
        var observer = mock(TriggerObserver.class);
        doThrow(new IllegalStateException("metrics offline")).when(observer).started(anyString(), any());
        doThrow(new IllegalStateException("metrics offline")).when(observer).finished(anyString(), any());
        var processor = mock(EventProcessor.class);
        var service = new EventTriggerService(store, processor, observer);
        var input = new TriggerInput(); input.triggerId = "observer"; input.eventId = "CustomerEvent";
        assertTrue(service.trigger(input).dispatched());
        assertEquals(TriggerLogStatus.COMPLETED, store.logs.get(0).status);
        assertTrue(service.trigger(input).duplicate());
        verify(processor).handleEvent(anyString(), any(), anyMap());
    }

    @Test void everyClaimStatusSuppressesRedispatchIncludingInterruptedClaims() {
        for (var status : TriggerLogStatus.values()) {
            var store = new InMemoryTriggerLogStore();
            var claim = new TriggerLog(); claim.triggerId = "existing"; claim.eventName = "CustomerEvent"; claim.status = status;
            store.record(claim);
            var processor = mock(EventProcessor.class);
            var service = new EventTriggerService(store, processor);
            var input = new TriggerInput(); input.triggerId = "existing"; input.eventId = "CustomerEvent";
            var result = service.trigger(input);
            assertTrue(result.duplicate());
            assertEquals(status == TriggerLogStatus.COMPLETED, result.dispatched());
            assertEquals(1, store.logs.size());
            verifyNoInteractions(processor);
        }
    }

    @Test
    void dispatchesTheOriginalPayloadOnlyOnce() {
        InMemoryTriggerLogStore logger = new InMemoryTriggerLogStore();
        TriggerObserver observer = mock(TriggerObserver.class);
        EventProcessor processor = mock(EventProcessor.class);
        when(processor.handleEvent(eq("CustomerEvent"), any(), anyMap())).thenReturn(List.of());
        EventTriggerService service = new EventTriggerService(logger, processor, observer);
        TriggerInput input = new TriggerInput();
        input.triggerId = "trigger-1";
        input.eventId = "CustomerEvent";
        input.headers.put("x-source", "HTTP");
        input.payload = Map.of("customerId", "customer-7", "triggerId", "trigger-1");

        assertTrue(service.trigger(input).dispatched());
        assertTrue(service.trigger(input).duplicate());
        var payload = org.mockito.ArgumentCaptor.forClass(Object.class);
        @SuppressWarnings("unchecked")
        var headers = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(processor, times(1)).handleEvent(eq("CustomerEvent"), payload.capture(), headers.capture());
        assertSame(input.payload, payload.getValue());
        assertEquals("trigger-1", headers.getValue().get("x-chenile-trigger-id"));
        assertEquals("HTTP", headers.getValue().get("x-source"));
        assertFalse(input.headers.containsKey("x-chenile-trigger-id"));
        input.headers.put("invalid-header", "value");
        assertThrows(IllegalArgumentException.class, () -> service.trigger(input));
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
        EventTriggerService service = new EventTriggerService(store, processor, observer);
        TriggerInput input = new TriggerInput();
        input.triggerId = "failed-trigger";
        input.eventId = "CustomerEvent";

        assertThrows(IllegalStateException.class, () -> service.trigger(input));
        assertEquals(TriggerLogStatus.FAILED, store.logs.get(0).status);
        assertEquals("subscriber failed", store.logs.get(0).error);
        assertNotNull(store.logs.get(0).finishedAt);
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
        EventTriggerService service = new EventTriggerService(store, processor);
        TriggerInput input = new TriggerInput();
        input.triggerId = "trigger-1";
        input.eventId = "CustomerEvent";

        var result = service.trigger(input);
        assertTrue(result.duplicate());
        assertFalse(result.dispatched());
        assertEquals("winning-claim", result.logId());
        verifyNoInteractions(processor);
    }

    static class InMemoryTriggerLogStore implements TriggerLogStore {
        final List<TriggerLog> logs = new ArrayList<>();
        @Override public TriggerLog record(TriggerLog log) { log.id = "log-" + logs.size(); logs.add(log); return log; }
        @Override public Optional<TriggerLog> findByTriggerIdAndEventName(String triggerId, String eventName) {
            return logs.stream().filter(l -> Objects.equals(l.triggerId, triggerId) && Objects.equals(l.eventName, eventName)).findFirst();
        }
        @Override public TriggerLog markRunning(String id) { TriggerLog log = find(id); log.status = TriggerLogStatus.RUNNING; log.startedAt = java.time.Instant.now(); return log; }
        @Override public TriggerLog markCompleted(String id) { TriggerLog log = find(id); log.status = TriggerLogStatus.COMPLETED; log.finishedAt = java.time.Instant.now(); return log; }
        @Override public TriggerLog markFailed(String id, RuntimeException failure) { TriggerLog log = find(id); log.status = TriggerLogStatus.FAILED; log.error = failure.getMessage(); log.finishedAt = java.time.Instant.now(); return log; }
        private TriggerLog find(String id) { return logs.stream().filter(l -> l.id.equals(id)).findFirst().orElseThrow(); }
    }
}
