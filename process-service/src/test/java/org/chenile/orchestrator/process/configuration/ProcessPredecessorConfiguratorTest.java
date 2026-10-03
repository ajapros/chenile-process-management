package org.chenile.orchestrator.process.configuration;

import org.chenile.orchestrator.process.config.reader.ProcessConfigurator;
import org.chenile.orchestrator.process.config.model.PredecessorArgs;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class ProcessPredecessorConfiguratorTest {
    @Test
    void findsOnlyDefinitionsWhosePredecessorMatchesTheCompletedType() throws Exception {
        ProcessConfigurator configurator = new ProcessConfigurator();
        configurator.read(new ByteArrayInputStream("""
                {"processMap":{
                  "import": {"leaf":true},
                  "index": {"predecessorProcessType":"import","predecessorArgs":"OUTPUT","leaf":true,"config":{"batchSize":"100"}},
                  "notify": {"predecessorProcessType":"import","predecessorArgs":"INPUT"},
                  "unrelated": {"predecessorProcessType":"other"}
                }}
                """.getBytes(StandardCharsets.UTF_8)));
        assertEquals(Set.of("index", "notify"), configurator.findByPredecessorProcessType("import")
                .stream().map(def -> def.processType).collect(Collectors.toSet()));
        assertEquals("100", configurator.findByName("index").config.get("batchSize"));
        assertEquals(PredecessorArgs.OUTPUT, configurator.findByName("index").predecessorArgs);
        assertEquals(PredecessorArgs.INPUT, configurator.findByName("notify").predecessorArgs);
        assertEquals(PredecessorArgs.BOTH, configurator.findByName("import").predecessorArgs);
        assertEquals(List.of(), configurator.findByPredecessorProcessType("unknown"));
        assertEquals(List.of(), configurator.findByPredecessorProcessType(null));
    }
}
