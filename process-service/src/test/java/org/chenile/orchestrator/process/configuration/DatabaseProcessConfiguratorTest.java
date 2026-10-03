package org.chenile.orchestrator.process.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.config.model.PredecessorArgs;
import org.chenile.orchestrator.process.configuration.dao.ProcessDefinitionRepository;
import org.chenile.orchestrator.process.configuration.model.ProcessDefinition;
import org.chenile.orchestrator.process.service.defs.DatabaseProcessConfigurator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DatabaseProcessConfiguratorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void cachesNamedDefinitionsAndMissingNames() {
        ProcessDefinitionRepository repository = mock(ProcessDefinitionRepository.class);
        when(repository.findById("import")).thenReturn(Optional.of(new ProcessDefinition("import", "{\"leaf\":true}")));
        when(repository.findById("missing")).thenReturn(Optional.empty());
        DatabaseProcessConfigurator configurator = new DatabaseProcessConfigurator(repository);
        ProcessDef first = configurator.findByName("import");
        assertSame(first, configurator.findByName("import"));
        assertNull(configurator.findByName("missing"));
        assertNull(configurator.findByName("missing"));
        verify(repository, times(1)).findById("import");
        verify(repository, times(1)).findById("missing");
    }

    @Test
    void loadsAllDefinitionsOnceAndSharesThemBetweenBothLookupMethods() {
        ProcessDefinitionRepository repository = mock(ProcessDefinitionRepository.class);
        when(repository.findAll()).thenReturn(List.of(new ProcessDefinition("index",
                "{\"predecessorProcessType\":\"import\"}")));
        DatabaseProcessConfigurator configurator = new DatabaseProcessConfigurator(repository);
        ProcessDef index = configurator.findByPredecessorProcessType("import").get(0);
        assertSame(index, configurator.findByName("index"));
        assertEquals(List.of(index), configurator.findByPredecessorProcessType("import"));
        assertEquals(List.of(), configurator.findByPredecessorProcessType("unknown"));
        assertNull(configurator.findByName("missing"));
        verify(repository, times(1)).findAll();
        verify(repository, never()).findById(anyString());
    }

    @Test
    void clearingTheCacheReloadsChangedDefinitionsAndPreviouslyMissingNames() {
        ProcessDefinitionRepository repository = mock(ProcessDefinitionRepository.class);
        when(repository.findById("index")).thenReturn(Optional.empty());
        when(repository.findAll()).thenReturn(
                List.of(new ProcessDefinition("index", "{\"predecessorProcessType\":\"import\"}")),
                List.of(new ProcessDefinition("index", "{\"predecessorProcessType\":\"other\"}")));
        DatabaseProcessConfigurator configurator = new DatabaseProcessConfigurator(repository);
        assertNull(configurator.findByName("index"));
        configurator.clearCache();
        assertEquals(1, configurator.findByPredecessorProcessType("import").size());
        assertEquals("import", configurator.findByName("index").predecessorProcessType);
        configurator.clearCache();
        assertEquals(List.of(), configurator.findByPredecessorProcessType("import"));
        assertEquals(1, configurator.findByPredecessorProcessType("other").size());
        assertEquals("other", configurator.findByName("index").predecessorProcessType);
        verify(repository, times(2)).findAll();
    }

    @Test
    void failedDefinitionLoadingDoesNotPoisonTheCache() {
        ProcessDefinitionRepository repository = mock(ProcessDefinitionRepository.class);
        when(repository.findAll()).thenReturn(
                List.of(new ProcessDefinition("index", "not JSON")),
                List.of(new ProcessDefinition("index", "{\"predecessorProcessType\":\"import\"}")));
        DatabaseProcessConfigurator configurator = new DatabaseProcessConfigurator(repository);
        assertThrows(org.chenile.base.exception.ConfigurationException.class,
                () -> configurator.findByPredecessorProcessType("import"));
        assertEquals(1, configurator.findByPredecessorProcessType("import").size());
        verify(repository, times(2)).findAll();
    }

    @Test
    void readsTheSameDefinitionShapeAsTheJsonConfigurator() throws Exception {
        ProcessDef definition = new ProcessDef();
        definition.leaf = true;
        definition.predecessorArgs = PredecessorArgs.OUTPUT;
        definition.config.put("batchSize", "100");

        DatabaseProcessConfigurator configurator = new DatabaseProcessConfigurator(
                repository(Map.of("import", new ProcessDefinition("import", objectMapper.writeValueAsString(definition)))),
                objectMapper);

        ProcessDef actual = configurator.findByName("import");

        assertEquals("import", actual.processType);
        assertEquals(true, actual.leaf);
        assertEquals("100", actual.config.get("batchSize"));
        assertEquals(PredecessorArgs.OUTPUT, actual.predecessorArgs);
        assertNull(configurator.findByName("missing"));
    }

    private ProcessDefinitionRepository repository(Map<String, ProcessDefinition> definitions) {
        return (ProcessDefinitionRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ProcessDefinitionRepository.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("findById")) return Optional.ofNullable(definitions.get(arguments[0]));
                    if (method.getName().equals("findAll")) return List.copyOf(definitions.values());
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void findsAllDatabaseDefinitionsWithTheCompletedTypeAsPredecessor() throws Exception {
        ProcessDef first = new ProcessDef(); first.predecessorProcessType = "import";
        ProcessDef second = new ProcessDef(); second.predecessorProcessType = "import";
        ProcessDef unrelated = new ProcessDef(); unrelated.predecessorProcessType = "other";
        DatabaseProcessConfigurator configurator = new DatabaseProcessConfigurator(repository(Map.of(
                "index", new ProcessDefinition("index", objectMapper.writeValueAsString(first)),
                "notify", new ProcessDefinition("notify", objectMapper.writeValueAsString(second)),
                "unrelated", new ProcessDefinition("unrelated", objectMapper.writeValueAsString(unrelated)))), objectMapper);
        assertEquals(java.util.Set.of("index", "notify"), configurator.findByPredecessorProcessType("import")
                .stream().map(def -> def.processType).collect(java.util.stream.Collectors.toSet()));
        assertEquals(List.of(), configurator.findByPredecessorProcessType("unknown"));
        assertEquals(List.of(), configurator.findByPredecessorProcessType(null));
    }
}
