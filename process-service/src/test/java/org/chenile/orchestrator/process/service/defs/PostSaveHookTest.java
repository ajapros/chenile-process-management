package org.chenile.orchestrator.process.service.defs;

import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.config.reader.IProcessConfigurator;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.WorkerType;
import org.chenile.stm.State;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PostSaveHookTest {
    @Test
    void passesTheSameConfigurationToEveryWorkerWithoutMutatingTheDefinition() {
        ProcessDef definition = new ProcessDef();
        definition.config = Map.of("shared", "value", "topic", "default");
        assertDispatch(definition, Constants.States.SPLIT_PENDING, WorkerType.SPLITTER);
        assertDispatch(definition, Constants.States.EXECUTING, WorkerType.EXECUTOR);
        assertDispatch(definition, Constants.States.AGGREGATION_PENDING, WorkerType.AGGREGATOR);
        assertEquals("default", definition.config.get("topic"));
    }

    @Test
    void allowsMissingConfiguration() {
        ProcessDef definition = new ProcessDef();
        definition.config = null;
        WorkerDto dto = dispatch(definition, Constants.States.EXECUTING);
        assertEquals(Map.of(), dto.execDef);
    }

    private void assertDispatch(ProcessDef definition, String state, WorkerType workerType) {
        WorkerDto dto = dispatch(definition, state);
        assertEquals(workerType, dto.workerType);
        assertEquals(definition.config, dto.execDef);
        dto.execDef.put("workerLocal", "value");
        assertFalse(definition.config.containsKey("workerLocal"));
    }

    private WorkerDto dispatch(ProcessDef definition, String state) {
        PostSaveHook hook = new PostSaveHook();
        hook.processConfigurator = mock(IProcessConfigurator.class);
        when(hook.processConfigurator.findByName("feed")).thenReturn(definition);
        WorkerStarter starter = mock(WorkerStarter.class);
        hook.setWorkerStarter(starter);
        Process process = new Process("feed", false);
        process.setCurrentState(new State(state, "process"));
        hook.dispatch(hook.prepare(process));
        ArgumentCaptor<WorkerDto> capture = ArgumentCaptor.forClass(WorkerDto.class);
        verify(starter).start(capture.capture());
        return capture.getValue();
    }
}
