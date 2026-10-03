package org.chenile.orchestrator.process.service.impl;

import org.chenile.core.context.ContextContainer;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.ProcessDto;
import org.chenile.stm.STM;
import org.chenile.stm.impl.STMActionsInfoProvider;
import org.chenile.utils.entity.service.EntityStore;
import org.chenile.workflow.dto.StateEntityServiceResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProcessManagerImplTriggerTest {
    @Test
    void createsARootFromTheDtoWithoutUsingATriggerLedger() {
        ProcessManagerImpl manager = manager();
        ProcessDto dto = new ProcessDto();
        dto.triggerId = "trigger-1";
        dto.processDefName = "daily-import";
        dto.args.put("batch", 10);
        manager.create(dto);
        ArgumentCaptor<Process> capture = ArgumentCaptor.forClass(Process.class);
        verify(manager).create(capture.capture());
        assertEquals("daily-import", capture.getValue().processType);
        assertEquals("trigger-1", capture.getValue().triggerId);
        assertEquals("{\"batch\":10}", capture.getValue().input);
    }

    @Test
    void repeatedDirectCreationDoesNotDeduplicateUsingTheTriggerId() {
        ProcessManagerImpl manager = manager();
        ProcessDto dto = new ProcessDto();
        dto.processDefName = "daily-import";
        dto.triggerId = "trigger-1";
        manager.create(dto);
        manager.create(dto);
        verify(manager, times(2)).create(any(Process.class));
        verify(manager, never()).retrieve(anyString());
    }

    @Test
    void usesTheAdapterHeaderForCorrelationButPreservesAnExplicitPayloadId() {
        ContextContainer context = ContextContainer.getInstance();
        var snapshot = context.snapshot();
        try {
            context.put("x-chenile-trigger-id", "crontab:nightly:fire-1");
            ProcessManagerImpl manager = manager();
            ProcessDto dto = new ProcessDto();
            dto.processDefName = "daily-import";
            manager.create(dto);
            dto.triggerId = "explicit-id";
            manager.create(dto);
            ArgumentCaptor<Process> capture = ArgumentCaptor.forClass(Process.class);
            verify(manager, times(2)).create(capture.capture());
            assertEquals("crontab:nightly:fire-1", capture.getAllValues().get(0).triggerId);
            assertEquals("explicit-id", capture.getAllValues().get(1).triggerId);
        } finally {
            context.restore(snapshot);
        }
    }

    @SuppressWarnings("unchecked")
    private ProcessManagerImpl manager() {
        ProcessManagerImpl manager = spy(new ProcessManagerImpl(mock(STM.class),
                mock(STMActionsInfoProvider.class), mock(EntityStore.class)));
        StateEntityServiceResponse<Process> response = mock(StateEntityServiceResponse.class);
        doReturn(response).when(manager).create(any(Process.class));
        return manager;
    }
}
