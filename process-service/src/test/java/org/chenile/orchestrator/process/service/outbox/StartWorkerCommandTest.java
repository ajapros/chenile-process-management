package org.chenile.orchestrator.process.service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.config.reader.IProcessConfigurator;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.WorkerType;
import org.chenile.orchestrator.process.outbox.OutboxCommand;
import org.chenile.orchestrator.process.outbox.ProcessOutboxRepository;
import org.chenile.stm.State;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StartWorkerCommandTest {
    private final IProcessConfigurator configurator = mock(IProcessConfigurator.class);
    private final DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
    private final WorkerStarter starter = mock(WorkerStarter.class);

    private Process process(String state) {
        Process process = new Process("feed", false);
        process.id = "process-1";
        process.setCurrentState(new State(state, "process"));
        return process;
    }

    private StartWorkerCommand command(ProcessOutboxRepository outbox) {
        return new StartWorkerCommand(outbox, null, new ObjectMapper(), mock(PlatformTransactionManager.class),
                configurator, beans.getBeanProvider(WorkerStarter.class));
    }

    private ProcessDef definition(Map<String, String> config) {
        ProcessDef definition = new ProcessDef();
        definition.config = config;
        when(configurator.findByName("feed")).thenReturn(definition);
        return definition;
    }

    @Test void mapsAllWorkerStatesAndCopiesConfigurationWithoutCloningInlineProcesses() throws Exception {
        beans.registerSingleton("starter", starter);
        var definition = definition(Map.of("shared", "value", "topic", "default"));
        var command = command(null);
        for (var mapping : Map.of(Constants.States.SPLIT_PENDING, WorkerType.SPLITTER,
                Constants.States.EXECUTING, WorkerType.EXECUTOR,
                Constants.States.AGGREGATION_PENDING, WorkerType.AGGREGATOR).entrySet()) {
            reset(starter);
            var process = process(mapping.getKey());
            command.enqueue(new ProcessTransition(process, process.getCurrentState()));
            var capture = ArgumentCaptor.forClass(WorkerDto.class);
            verify(starter).start(capture.capture());
            var worker = capture.getValue();
            assertSame(process, worker.process);
            assertEquals(mapping.getValue(), worker.workerType);
            assertEquals("process-1:" + mapping.getValue(), worker.dispatchId);
            assertEquals(definition.config, worker.execDef);
            worker.execDef.put("workerLocal", "value");
            assertFalse(definition.config.containsKey("workerLocal"));
        }
    }

    @Test void permitsMissingConfiguration() throws Exception {
        beans.registerSingleton("starter", starter);
        definition(null);
        var process = process(Constants.States.EXECUTING);
        command(null).enqueue(new ProcessTransition(process, process.getCurrentState()));
        var capture = ArgumentCaptor.forClass(WorkerDto.class);
        verify(starter).start(capture.capture());
        assertEquals(Map.of(), capture.getValue().execDef);
    }

    @Test void skipsSuppressedWorkTerminalStatesAndMissingDefinitions() throws Exception {
        beans.registerSingleton("starter", starter);
        definition(Map.of());
        var command = command(null);
        var suppressed = process(Constants.States.EXECUTING);
        suppressed.skipPostWorkerCreation = true;
        command.enqueue(new ProcessTransition(suppressed, suppressed.getCurrentState()));
        var terminal = process(Constants.States.PROCESSED);
        command.enqueue(new ProcessTransition(terminal, terminal.getCurrentState()));
        when(configurator.findByName("feed")).thenReturn(null);
        var unknown = process(Constants.States.EXECUTING);
        command.enqueue(new ProcessTransition(unknown, unknown.getCurrentState()));
        verifyNoInteractions(starter);
    }

    @Test void missingStarterIsAnInlineNoOp() throws Exception {
        var process = process(Constants.States.EXECUTING);
        command(null).enqueue(new ProcessTransition(process, process.getCurrentState()));
        verifyNoInteractions(configurator);
    }

    @Test void durableWorkSnapshotsConfigurationAndMissingStarterFailsOnlyAtDispatch() throws Exception {
        var definition = definition(Map.of("topic", "original"));
        var outbox = mock(ProcessOutboxRepository.class);
        when(outbox.receive(eq("WORKER"), anyString())).thenReturn(true);
        var command = command(outbox);
        var process = process(Constants.States.EXECUTING);
        command.enqueue(new ProcessTransition(process, process.getCurrentState()));
        var capture = ArgumentCaptor.forClass(OutboxCommand.class);
        verify(outbox).enqueue(capture.capture());
        definition.config = Map.of("topic", "changed");
        assertEquals("original", new ObjectMapper().readTree(capture.getValue().payload).path("execDef").path("topic").asText());
        var failure = assertThrows(IllegalStateException.class, () -> command.dispatch(capture.getValue()));
        assertEquals("No WorkerStarter configured for durable dispatch", failure.getMessage());

        beans.registerSingleton("starter", starter);
        command.dispatch(capture.getValue());
        var work = ArgumentCaptor.forClass(WorkerDto.class);
        verify(starter).start(work.capture());
        assertEquals("original", work.getValue().execDef.get("topic"));
    }

    @Test void existingReceiptPreventsWorkerDispatch() throws Exception {
        beans.registerSingleton("starter", starter);
        definition(Map.of());
        var outbox = mock(ProcessOutboxRepository.class);
        var command = command(outbox);
        var process = process(Constants.States.EXECUTING);
        command.enqueue(new ProcessTransition(process, process.getCurrentState()));
        var capture = ArgumentCaptor.forClass(OutboxCommand.class);
        verify(outbox).enqueue(capture.capture());
        command.dispatch(capture.getValue());
        verifyNoInteractions(starter);
    }

    @Test void multipleStartersRequireAnExplicitPrimary() throws Exception {
        beans.registerSingleton("one", starter);
        beans.registerSingleton("two", mock(WorkerStarter.class));
        definition(Map.of());
        var command = command(null);
        var process = process(Constants.States.EXECUTING);
        var transition = new ProcessTransition(process, process.getCurrentState());
        assertThrows(NoUniqueBeanDefinitionException.class, () -> command.enqueue(transition));
        var selected = new RootBeanDefinition(WorkerStarter.class, () -> starter);
        selected.setPrimary(true);
        beans.registerBeanDefinition("selected", selected);
        command.enqueue(transition);
        verify(starter).start(any());
    }
}
