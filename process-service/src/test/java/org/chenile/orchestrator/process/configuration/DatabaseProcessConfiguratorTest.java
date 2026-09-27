package org.chenile.orchestrator.process.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.configuration.dao.ProcessDefinitionRepository;
import org.chenile.orchestrator.process.configuration.model.ProcessDefinition;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DatabaseProcessConfiguratorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsTheSameDefinitionShapeAsTheJsonConfigurator() throws Exception {
        ProcessDef definition = new ProcessDef();
        definition.leaf = true;
        definition.successors.add("archive");
        definition.executorConfig.put("batchSize", "100");

        DatabaseProcessConfigurator configurator = new DatabaseProcessConfigurator(
                repository(Map.of("import", new ProcessDefinition("import", objectMapper.writeValueAsString(definition)))),
                objectMapper);

        ProcessDef actual = configurator.findByName("import");

        assertEquals("import", actual.processType);
        assertEquals(true, actual.leaf);
        assertEquals("archive", actual.successors.get(0));
        assertEquals("100", actual.executorConfig.get("batchSize"));
        assertNull(configurator.findByName("missing"));
    }

    private ProcessDefinitionRepository repository(Map<String, ProcessDefinition> definitions) {
        return (ProcessDefinitionRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ProcessDefinitionRepository.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("findById")) return Optional.ofNullable(definitions.get(arguments[0]));
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
