package org.chenile.orchestrator.process.service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.core.context.HeaderUtils;
import org.chenile.core.event.EventProcessor;
import org.chenile.orchestrator.process.api.ProcessManager;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.ProcessCompletedEvent;
import org.chenile.orchestrator.process.outbox.OutboxCommand;
import org.chenile.orchestrator.process.outbox.ProcessOutboxRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmitCompletedCommandTest {
    @Test
    @SuppressWarnings("unchecked")
    void publishesAfterCommitInANewTransactionWithTenantAndTriggerHeaders() throws Exception {
        ProcessManager manager = mock(ProcessManager.class);
        ObjectProvider<ProcessManager> managers = mock(ObjectProvider.class);
        when(managers.getObject()).thenReturn(manager);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        var status = new SimpleTransactionStatus();
        when(transactions.getTransaction(any())).thenReturn(status);
        EventProcessor events = mock(EventProcessor.class);
        var command = new EmitCompletedCommand(mock(ProcessOutboxRepository.class), managers, new ObjectMapper(), transactions, events);
        var event = new ProcessCompletedEvent();
        event.processId = "process-1";
        event.tenantId = "tenant-1";
        event.triggerId = "trigger-1";
        var entry = OutboxCommand.create(event.processId, EmitCompletedCommand.TYPE, "process-1:completed", null);
        entry.tenantId = event.tenantId;
        entry.attachInlinePayload(event);

        TransactionSynchronizationManager.initSynchronization();
        try {
            command.dispatch(entry);
            verify(manager).processCompleted(event);
            verifyNoInteractions(events, transactions);
            var callbacks = TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, callbacks.size());
            callbacks.get(0).afterCommit();

            var definition = ArgumentCaptor.forClass(TransactionDefinition.class);
            verify(transactions).getTransaction(definition.capture());
            assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, definition.getValue().getPropagationBehavior());
            verify(events).handleEvent(Constants.Events.PROCESS_COMPLETED, event,
                    Map.of(HeaderUtils.TENANT_ID_KEY, "tenant-1", "triggerId", "trigger-1"));
            verify(transactions).commit(status);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
