package org.chenile.orchestrator.process.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.config.model.PredecessorArgs;
import org.chenile.orchestrator.process.config.reader.IProcessConfigurator;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.ProcessCompletedEvent;
import org.chenile.stm.STM;
import org.chenile.stm.impl.STMActionsInfoProvider;
import org.chenile.utils.entity.service.EntityStore;
import org.chenile.workflow.dto.StateEntityServiceResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessManagerCompletionTest {
    @Test
    void startsEveryMatchingDefinitionWithBothPredecessorArgumentsAndLineage() throws Exception {
        ProcessManagerImpl manager = manager();
        when(manager.processConfigurator.findByPredecessorProcessType("import"))
                .thenReturn(List.of(definition("index"), definition("notify")));
        ProcessCompletedEvent event = event();
        event.input = "{\"batch\":10}";
        event.output = "{\"rows\":42}";

        assertEquals(2, manager.processCompleted(event).size());

        ArgumentCaptor<Process> capture = ArgumentCaptor.forClass(Process.class);
        verify(manager, times(2)).create(capture.capture());
        assertEquals(List.of("index", "notify"), capture.getAllValues().stream().map(p -> p.processType).toList());
        for (Process process : capture.getAllValues()) {
            assertEquals("predecessor-1", process.predecessorId);
            assertNull(process.parentId);
            assertEquals("tenant-1", process.tenant);
            assertEquals("trigger-1", process.triggerId);
            assertEquals(Map.of("input", Map.of("batch", 10), "output", Map.of("rows", 42)),
                    new ObjectMapper().readValue(process.input, Map.class));
        }
        assertEquals("import", event.processDefName);
        assertEquals(Map.of(), event.args);
    }

    @Test
    void completionWithoutAMatchingDefinitionDoesNotStartAnything() {
        ProcessManagerImpl manager = manager();
        when(manager.processConfigurator.findByPredecessorProcessType("import")).thenReturn(List.of());
        assertEquals(List.of(), manager.processCompleted(event()));
        verify(manager, never()).create(any(Process.class));
    }

    @Test
    void eachSuccessorSelectsItsOwnPredecessorArguments() throws Exception {
        ProcessManagerImpl manager = manager();
        ProcessDef inputOnly = definition("inputConsumer"); inputOnly.predecessorArgs = PredecessorArgs.INPUT;
        ProcessDef outputOnly = definition("outputConsumer"); outputOnly.predecessorArgs = PredecessorArgs.OUTPUT;
        ProcessDef both = definition("bothConsumer"); both.predecessorArgs = PredecessorArgs.BOTH;
        ProcessDef nullSelection = definition("defaultConsumer"); nullSelection.predecessorArgs = null;
        when(manager.processConfigurator.findByPredecessorProcessType("import"))
                .thenReturn(List.of(inputOnly, outputOnly, both, nullSelection));
        ProcessCompletedEvent event = event();
        event.input = "{\"batch\":10}";
        event.output = "{\"rows\":42}";
        manager.processCompleted(event);

        ArgumentCaptor<Process> capture = ArgumentCaptor.forClass(Process.class);
        verify(manager, times(4)).create(capture.capture());
        ObjectMapper mapper = new ObjectMapper();
        assertEquals(Map.of("input", Map.of("batch", 10)), mapper.readValue(capture.getAllValues().get(0).input, Map.class));
        assertEquals(Map.of("output", Map.of("rows", 42)), mapper.readValue(capture.getAllValues().get(1).input, Map.class));
        Map<String, Object> expectedBoth = Map.of("input", Map.of("batch", 10), "output", Map.of("rows", 42));
        assertEquals(expectedBoth, mapper.readValue(capture.getAllValues().get(2).input, Map.class));
        assertEquals(expectedBoth, mapper.readValue(capture.getAllValues().get(3).input, Map.class));
    }

    @Test
    void failedCompletionAlsoStartsMatchesAndPreservesPlainTextAndNullArguments() throws Exception {
        ProcessManagerImpl manager = manager();
        when(manager.processConfigurator.findByPredecessorProcessType("import")).thenReturn(List.of(definition("notify")));
        ProcessCompletedEvent event = event();
        event.successful = false;
        event.input = "plain text input";
        event.output = null;
        manager.processCompleted(event);
        ArgumentCaptor<Process> capture = ArgumentCaptor.forClass(Process.class);
        verify(manager).create(capture.capture());
        Map<?, ?> args = new ObjectMapper().readValue(capture.getValue().input, Map.class);
        assertEquals("plain text input", args.get("input"));
        assertTrue(args.containsKey("output"));
        assertNull(args.get("output"));
    }

    private ProcessCompletedEvent event() {
        ProcessCompletedEvent event = new ProcessCompletedEvent();
        event.processType = "import";
        event.processDefName = "import";
        event.processId = "predecessor-1";
        event.tenantId = "tenant-1";
        event.triggerId = "trigger-1";
        event.successful = true;
        return event;
    }

    private ProcessDef definition(String name) {
        ProcessDef definition = new ProcessDef();
        definition.processType = name;
        definition.predecessorProcessType = "import";
        return definition;
    }

    @SuppressWarnings("unchecked")
    private ProcessManagerImpl manager() {
        ProcessManagerImpl manager = spy(new ProcessManagerImpl(mock(STM.class),
                mock(STMActionsInfoProvider.class), mock(EntityStore.class)));
        manager.processConfigurator = mock(IProcessConfigurator.class);
        StateEntityServiceResponse<Process> response = mock(StateEntityServiceResponse.class);
        doReturn(response).when(manager).create(any(Process.class));
        return manager;
    }
}
