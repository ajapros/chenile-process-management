package org.chenile.orchestrator.process.service.entry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.core.context.HeaderUtils;
import org.chenile.core.event.EventProcessor;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.ProcessCompletedEvent;
import org.chenile.orchestrator.process.model.ProcessDto;
import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.config.reader.IProcessConfigurator;
import org.chenile.orchestrator.process.service.outbox.*;
import org.chenile.orchestrator.process.outbox.OutboxCommandRegistry;
import org.chenile.orchestrator.process.api.ProcessManager;
import org.chenile.orchestrator.process.configuration.dao.ProcessRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.chenile.stm.State;
import org.chenile.stm.impl.STMActionsInfoProvider;
import org.chenile.utils.entity.service.EntityStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessEntryActionTest {
    @Test
    @SuppressWarnings("unchecked")
    void savesProcessBeforeEnqueuingItsTransition() throws Exception {
        EntityStore<Process> store = mock(EntityStore.class);
        OutboxCommandRegistry<ProcessTransition> commands = mock(OutboxCommandRegistry.class);
        ProcessEntryAction action = new ProcessEntryAction(store, mock(STMActionsInfoProvider.class));
        ReflectionTestUtils.setField(action, "commands", commands);
        Process process = new Process("feed", false);
        State destination = new State(Constants.States.EXECUTING, "process");
        process.setCurrentState(destination);

        action.execute(null, destination, process);

        var order = inOrder(store, commands);
        order.verify(store).store(process);
        order.verify(commands).enqueue(new ProcessTransition(process, destination));
    }

    @Test
    void completionSubscribersReceiveTheProcessTenantAndTrigger() throws Exception {
        assertCompletion(Constants.States.PROCESSED, "tenant-1", true);
        assertCompletion(Constants.States.PROCESSED_WITH_ERRORS, "tenant-2", false);
    }

    @Test
    void completionWithoutTenantOmitsTheTenantHeader() throws Exception {
        assertCompletion(Constants.States.PROCESSED, null, true);
    }

    @SuppressWarnings("unchecked")
    private void assertCompletion(String stateId, String tenant, boolean successful) throws Exception {
        EntityStore<Process> store = mock(EntityStore.class);
        ProcessEntryAction action = new ProcessEntryAction(store, mock(STMActionsInfoProvider.class));
        EventProcessor processor = mock(EventProcessor.class);
        ObjectProvider<ProcessManager> managers = mock(ObjectProvider.class);
        var mapper = new ObjectMapper();
        var transactions = mock(PlatformTransactionManager.class);
        var commands = new OutboxCommandRegistry<ProcessTransition>(List.of(
                new CreateSubProcessCommand(null, managers, mapper, transactions, mock(ProcessRepository.class)), new SignalParentCommand(null, managers, mapper, transactions),
                new EmitCompletedCommand(null, managers, mapper, transactions, processor), new StartWorkerCommand(null, managers, mapper, transactions, mock(IProcessConfigurator.class), mock(ObjectProvider.class))));
        ReflectionTestUtils.setField(action, "commands", commands);
        Process process = new Process("feed", false);
        process.setId("process-1");
        process.tenant = tenant;
        process.triggerId = "trigger-1";
        process.input = "{\"batch\":10}";
        process.output = "{\"rows\":42}";
        State terminal = new State(stateId, "process");
        process.setCurrentState(terminal);

        action.execute(new State(Constants.States.EXECUTING, "process"), terminal, process);

        ArgumentCaptor<ProcessCompletedEvent> event = ArgumentCaptor.forClass(ProcessCompletedEvent.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(processor).handleEvent(eq(Constants.Events.PROCESS_COMPLETED), event.capture(), headers.capture());
        assertEquals("process-1", event.getValue().processId);
        ProcessDto dto = event.getValue();
        assertEquals("feed", dto.processDefName);
        assertEquals(Map.of("input", Map.of("batch", 10), "output", Map.of("rows", 42)), dto.args);
        assertEquals(tenant, event.getValue().tenantId);
        assertEquals(successful, event.getValue().successful);
        assertEquals("trigger-1", headers.getValue().get("triggerId"));
        if (tenant == null) assertFalse(headers.getValue().containsKey(HeaderUtils.TENANT_ID_KEY));
        else assertEquals(tenant, headers.getValue().get(HeaderUtils.TENANT_ID_KEY));
        verify(store).store(process);
    }
}
