package org.chenile.orchestrator.process.service.impl;

import org.chenile.orchestrator.process.SpringTestConfig;
import org.chenile.orchestrator.process.api.ProcessManager;
import org.chenile.orchestrator.process.config.reader.ProcessConfigurator;
import org.chenile.orchestrator.process.configuration.dao.ProcessRepository;
import org.chenile.orchestrator.process.model.*;
import org.chenile.orchestrator.process.model.payload.*;
import org.chenile.orchestrator.process.outbox.*;
import org.chenile.orchestrator.process.service.defs.PostSaveHook;
import org.chenile.orchestrator.process.service.outbox.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.test.context.ActiveProfiles;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = SpringTestConfig.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:inline-process-commands;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("unittest")
class InlineProcessCommandIntegrationTest {
    @Autowired ProcessManager manager;
    @Autowired ProcessConfigurator configurator;
    @Autowired ProcessRepository processes;
    @Autowired PostSaveHook workers;
    @Autowired ProcessCommandSupport support;
    @Autowired OutboxCommandRegistry<ProcessTransition> commands;
    @Autowired ApplicationContext application;
    @Autowired JdbcTemplate jdbc;

    @Test void deadLifecycleWorksWithoutTheOutboxTransport() throws Exception {
        var original = configurator.processes;
        List<WorkerDto> dispatched = new ArrayList<>();
        try {
            configurator.read(new ByteArrayInputStream("""
                {"processMap":{"leaf":{"leaf":true}}}
                """.getBytes(StandardCharsets.UTF_8)));
            workers.setWorkerStarter(dispatched::add);
            ProcessDto dto = new ProcessDto(); dto.processDefName = "leaf";
            var process = manager.create(dto).getMutatedEntity();
            OutboxCommand entry = OutboxCommand.create(process.getId(), StartWorkerCommand.TYPE,
                    dispatched.get(0).dispatchId, null);
            entry.tenantId = process.tenant;
            entry.attachInlinePayload(dispatched.get(0));
            commands.resolve(StartWorkerCommand.TYPE).onDead(entry);
            assertEquals(Constants.States.PROCESSED_WITH_ERRORS,
                    processes.findById(process.getId()).orElseThrow().getCurrentState().getStateId());
            assertFalse(processes.findById(process.getId()).orElseThrow().errors.isEmpty());
            for (String type : List.of(CreateSubProcessCommand.TYPE, SignalParentCommand.TYPE, EmitCompletedCommand.TYPE))
                assertDoesNotThrow(() -> commands.resolve(type).onDead(entry));
            assertFalse(support.durable());
        } finally {
            configurator.processes = original;
            workers.setWorkerStarter(null);
        }
    }

    @Test void allFourCommandsRunSynchronouslyWithoutAnOutboxRepositoryOrSchema() throws Exception {
        assertFalse(support.durable());
        assertEquals(0, application.getBeanNamesForType(ProcessOutboxRepository.class).length);
        assertEquals(0, application.getBeanNamesForType(OutboxDispatcher.class).length);
        for (String type : List.of(CreateSubProcessCommand.TYPE, SignalParentCommand.TYPE,
                EmitCompletedCommand.TYPE, StartWorkerCommand.TYPE)) assertNotNull(commands.resolve(type));
        for (String table : List.of("CHENILE_PROCESS_OUTBOX", "CHENILE_PROCESS_RECEIPT"))
            assertFalse(jdbc.execute((ConnectionCallback<Boolean>) connection -> {
                try (var tables = connection.getMetaData().getTables(null, null, table, null)) { return tables.next(); }
            }));

        var original = configurator.processes;
        List<WorkerDto> dispatched = new ArrayList<>();
        try {
            configurator.read(new ByteArrayInputStream("""
                {"processMap":{"parent":{"leaf":false},"child":{"leaf":true},
                 "index":{"leaf":true,"predecessorProcessType":"parent","predecessorArgs":"OUTPUT"}}}
                """.getBytes(StandardCharsets.UTF_8)));
            workers.setWorkerStarter(dispatched::add);
            ProcessDto dto = new ProcessDto(); dto.processDefName = "parent"; dto.triggerId = "inline-trigger";
            var parent = manager.create(dto).getMutatedEntity();
            String id = parent.getId();
            assertSame(parent, dispatched.get(0).process, "inline workers must receive the live process, not a JSON clone");
            assertEquals(WorkerType.SPLITTER, dispatched.get(0).workerType);

            StartProcessingPayload split = new StartProcessingPayload();
            SubProcessPayload one = new SubProcessPayload(); one.childId = "inline-child-1"; one.processType = "child";
            SubProcessPayload two = new SubProcessPayload(); two.childId = "inline-child-2"; two.processType = "child";
            split.subProcesses = List.of(one, two);
            manager.processById(id, Constants.Events.SPLIT_DONE, split);
            assertEquals(2, processes.findByParentId(id).size(), "children are created before the call returns");
            assertEquals(3, dispatched.size());
            manager.processById(one.childId, Constants.Events.DONE_SUCCESSFULLY, new DoneSuccessfullyPayload());
            DoneWithErrorsPayload failed = new DoneWithErrorsPayload(); failed.exceptionMessage = "child failed";
            manager.processById(two.childId, Constants.Events.DONE_WITH_ERRORS, failed);
            var aggregating = processes.findById(id).orElseThrow();
            assertEquals(2, aggregating.numCompletedSubProcesses);
            assertFalse(aggregating.errors.isEmpty());
            assertEquals(Constants.States.AGGREGATION_PENDING, aggregating.getCurrentState().getStateId());
            assertEquals(WorkerType.AGGREGATOR, dispatched.get(3).workerType);

            AggregationDonePayload done = new AggregationDonePayload(); done.output = "{\"rows\":2}";
            manager.processById(id, Constants.Events.AGGREGATION_DONE, done);
            assertEquals(Constants.States.PROCESSED_WITH_ERRORS, processes.findById(id).orElseThrow().getCurrentState().getStateId());
            var successors = processes.findByPredecessorId(id);
            assertEquals(1, successors.size(), "inline publication must not create a successor twice");
            assertEquals("{\"output\":{\"rows\":2}}", successors.get(0).input);
            assertEquals("inline-trigger", successors.get(0).triggerId);
            assertEquals(5, dispatched.size());
        } finally {
            configurator.processes = original;
            workers.setWorkerStarter(null);
        }
    }
}
