package org.chenile.orchestrator.process.service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.core.context.ChenileExchange;
import org.chenile.core.context.HeaderUtils;
import org.chenile.core.event.EventProcessor;
import org.chenile.orchestrator.process.api.ProcessManager;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.ProcessCompletedEvent;
import org.chenile.orchestrator.process.outbox.OutboxCommand;
import org.chenile.orchestrator.process.outbox.ProcessOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmitCompletedCommandTest {
    @Test @SuppressWarnings("unchecked")
    void publishesThroughEventProcessingAndPropagatesSubscriberFailures() throws Exception {
        ObjectProvider<ProcessManager> managers = mock(ObjectProvider.class);
        EventProcessor events = mock(EventProcessor.class);
        var mapper = new ObjectMapper();
        var command = new EmitCompletedCommand(mock(ProcessOutboxRepository.class), managers, mapper,
                mock(PlatformTransactionManager.class), events);
        var event = new ProcessCompletedEvent();
        event.processId = "process-1"; event.tenantId = "tenant-1"; event.triggerId = "trigger-1";
        var entry = OutboxCommand.create(event.processId, EmitCompletedCommand.TYPE,
                "process-1:completed", mapper.writeValueAsString(event));
        entry.tenantId = event.tenantId;
        when(events.handleEvent(anyString(), any(), anyMap())).thenReturn(List.of(new ChenileExchange()));
        command.dispatch(entry);
        var captured = org.mockito.ArgumentCaptor.forClass(ProcessCompletedEvent.class);
        verify(events).handleEvent(eq(Constants.Events.PROCESS_COMPLETED), captured.capture(),
                eq(Map.of(HeaderUtils.TENANT_ID_KEY, "tenant-1", "triggerId", "trigger-1")));
        assertEquals(event.processId, captured.getValue().processId);
        verifyNoInteractions(managers);

        var failed = new ChenileExchange();
        var failure = new IllegalStateException("subscriber unavailable"); failed.setException(failure);
        when(events.handleEvent(anyString(), any(), anyMap())).thenReturn(List.of(failed));
        assertSame(failed.getException(), assertThrows(IllegalStateException.class, () -> command.dispatch(entry)).getCause());
        when(events.handleEvent(anyString(), any(), anyMap())).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> command.dispatch(entry));
    }
}
