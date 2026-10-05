package org.chenile.orchestrator.process.configuration;

import org.chenile.orchestrator.process.config.model.Processes;
import org.chenile.orchestrator.process.config.reader.ProcessConfigurator;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ProcessConfiguratorDocumentTest {
    public static class ExtendedDocument extends Processes {
        public Map<String,String> metadata;
    }
    private ByteArrayInputStream json(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
    @Test void typedExtensionUsesNormalDefinitionsAndLookup() throws Exception {
        var reader = new ProcessConfigurator();
        var document = reader.read(json("""
            {"processMap":{"Root":{"leaf":true},"Next":{"leaf":true,"predecessorProcessType":"Root"}},
             "metadata":{"owner":"example"}}
            """), ExtendedDocument.class);
        assertSame(document, reader.processes);
        assertEquals("example", document.metadata.get("owner"));
        assertEquals("Root", reader.findByName("Root").processType);
        assertEquals("Next", reader.findByPredecessorProcessType("Root").getFirst().processType);
    }
    @Test void nativeDocumentAndDefaultsRemainSupported() throws Exception {
        var reader = new ProcessConfigurator();
        reader.read(json("{\"processMap\":{\"Root\":{\"leaf\":true,\"config\":{\"opaque\":\"value\"}}}}"));
        assertEquals("value", reader.findByName("Root").config.get("opaque"));
        assertEquals(org.chenile.orchestrator.process.config.model.PredecessorArgs.BOTH, reader.findByName("Root").predecessorArgs);
    }
    @Test void rejectsDuplicateDefinitionsBeforeReplacingLoadedConfiguration() throws Exception {
        var reader = new ProcessConfigurator();
        reader.read(json("{\"processMap\":{\"Root\":{\"leaf\":true}}}"));
        assertThrows(java.io.IOException.class, () -> reader.read(json("{\"processMap\":{\"A\":{},\"A\":{}}}")));
        assertNotNull(reader.findByName("Root"));
    }
    @Test void ignoresGenerationMetadataAndUnknownPropertiesAtEveryLevel() throws Exception {
        var reader = new ProcessConfigurator();
        reader.read(json("""
            {"future":{"nested":true},"inputFields":{"feed":{"batchId":"string"}},
             "processMap":{"feed":{"leaf":true,"inputFields":{"batchId":"string"},"future":"ignored"}}}
            """));
        assertEquals("feed", reader.findByName("feed").processType);
        assertTrue(reader.findByName("feed").leaf);
    }
}
