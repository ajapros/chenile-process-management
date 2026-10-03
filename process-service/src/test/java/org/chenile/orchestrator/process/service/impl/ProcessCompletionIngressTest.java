package org.chenile.orchestrator.process.service.impl;

import org.chenile.core.context.HeaderUtils;
import org.chenile.core.event.EventProcessor;
import org.chenile.orchestrator.process.SpringTestConfig;
import org.chenile.orchestrator.process.config.reader.ProcessConfigurator;
import org.chenile.orchestrator.process.configuration.dao.ProcessRepository;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.ProcessCompletedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = SpringTestConfig.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:completion-ingress;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("unittest")
class ProcessCompletionIngressTest {
    @Autowired EventProcessor eventProcessor;
    @Autowired ProcessConfigurator configurator;
    @Autowired ProcessRepository repository;

    @Test
    void theRegisteredCompletionSubscriberCreatesAProcessUsingTheMatchingDefinition() throws Exception {
        var original = configurator.processes;
        try {
            configurator.read(new ByteArrayInputStream("""
                    {"processMap":{"index":{"leaf":true,"predecessorProcessType":"import","predecessorArgs":"OUTPUT"}}}
                    """.getBytes(StandardCharsets.UTF_8)));
            ProcessCompletedEvent event = new ProcessCompletedEvent();
            event.processId = "completed-import";
            event.processType = "import";
            event.triggerId = "run-1";
            event.tenantId = "tenant-1";
            event.input = "{\"batch\":7}";
            event.output = "{\"rows\":100}";
            event.successful = true;

            var exchanges = eventProcessor.handleEvent(Constants.Events.PROCESS_COMPLETED, event,
                    Map.of(HeaderUtils.TENANT_ID_KEY, event.tenantId));

            assertFalse(exchanges.isEmpty(), "ProcessCompleted must have a registered subscriber");
            for (var exchange : exchanges) assertNull(exchange.getException());
            var processes = repository.findByPredecessorId("completed-import");
            assertEquals(1, processes.size());
            var process = processes.get(0);
            assertEquals("index", process.processType);
            assertTrue(process.leaf);
            assertEquals("tenant-1", process.tenant);
            assertEquals("run-1", process.triggerId);
            assertEquals(Constants.States.EXECUTING, process.getCurrentState().getStateId());
            assertEquals("{\"output\":{\"rows\":100}}", process.input);
        } finally {
            configurator.processes = original;
        }
    }
}
