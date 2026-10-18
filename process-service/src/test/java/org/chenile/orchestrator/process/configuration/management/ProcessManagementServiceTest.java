package org.chenile.orchestrator.process.configuration.management;

import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.config.reader.ProcessConfigurator;
import org.chenile.orchestrator.process.configuration.dao.*;
import org.chenile.orchestrator.process.model.ProcessDto;
import org.chenile.trigger.TriggerService;
import org.chenile.trigger.cron.*;
import org.chenile.orchestrator.process.outbox.ProcessOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessManagementServiceTest {
    @Test void completionTimeComesOnlyFromTerminalStateEntryAndPreservesUnknownLegacyTimes() {
        var process = new org.chenile.orchestrator.process.model.Process();
        var entered = java.time.Instant.parse("2026-10-14T00:00:00Z");
        process.setStateEntryTime(java.util.Date.from(entered));
        for (String state : java.util.List.of("PROCESSED", "PROCESSED_WITH_ERRORS")) {
            process.setCurrentState(new org.chenile.stm.State(state, "process"));
            assertEquals(entered, ProcessManagementService.ProcessView.of(process).completedAt());
        }
        process.setCurrentState(new org.chenile.stm.State("EXECUTING", "process"));
        assertNull(ProcessManagementService.ProcessView.of(process).completedAt());
        process.setCurrentState(new org.chenile.stm.State("PROCESSED", "process"));
        process.setStateEntryTime(null);
        assertNull(ProcessManagementService.ProcessView.of(process).completedAt());
    }

    @Test void jsonDefinitionsRemainInspectableButNeverPretendDatabaseWritesAreEffective() {
        var configurator = new ProcessConfigurator();
        var definition = new ProcessDef(); definition.processType = "source"; definition.leaf = true;
        configurator.processes.processMap.put("source", definition);
        var definitions = mock(ProcessDefinitionRepository.class);
        @SuppressWarnings("unchecked") ObjectProvider<CrontabService> cron = mock(ObjectProvider.class);
        var service = new ProcessManagementService(mock(ProcessRepository.class),
                definitions, configurator, mock(CrontabRepository.class), cron,
                mock(ProcessOutboxRepository.class), mock(TriggerService.class));
        assertEquals("json", service.info().get("definitionSource"));
        assertEquals(false, service.info().get("definitionsWritable"));
        assertEquals(false, service.info().get("cronAvailable"));
        assertEquals(java.util.List.of(definition), service.definitions());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.saveDefinition("source", definition, false)).getStatusCode().value());
        verifyNoInteractions(definitions);
        ProcessDto dto = new ProcessDto(); dto.processDefName = "source";
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> service.saveCron("alpha", null,
                new ProcessManagementService.CronRequest("daily", "0 0 * * * ?", "UTC", true, dto))).getStatusCode().value());
    }
}
