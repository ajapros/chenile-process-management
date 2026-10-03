package org.chenile.orchestrator.process.service.defs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.base.exception.ConfigurationException;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.config.reader.ProcessConfigurator;
import org.chenile.orchestrator.process.configuration.dao.ProcessDefinitionRepository;
import org.chenile.orchestrator.process.configuration.model.ProcessDefinition;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Resolves process definitions from the {@code process_definition} table. */
public class DatabaseProcessConfigurator extends ProcessConfigurator {
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, ProcessDef> cache = new HashMap<>();
    private boolean allDefinitionsLoaded;

    public DatabaseProcessConfigurator(ProcessDefinitionRepository processDefinitionRepository) {
        this(processDefinitionRepository, new ObjectMapper());
    }

    public DatabaseProcessConfigurator(ProcessDefinitionRepository processDefinitionRepository, ObjectMapper objectMapper) {
        this.processDefinitionRepository = processDefinitionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized ProcessDef findByName(String name) {
        if (allDefinitionsLoaded || cache.containsKey(name)) return cache.get(name);
        ProcessDef definition = processDefinitionRepository.findById(name)
                .map(this::toProcessDef)
                .orElse(null);
        cache.put(name, definition);
        return definition;
    }

    @Override
    public synchronized List<ProcessDef> findByPredecessorProcessType(String processType) {
        if (processType == null || processType.isBlank()) return List.of();
        if (!allDefinitionsLoaded) {
            Map<String, ProcessDef> definitions = new HashMap<>();
            for (ProcessDefinition definition : processDefinitionRepository.findAll()) {
                ProcessDef processDef = toProcessDef(definition);
                definitions.put(processDef.processType, processDef);
            }
            cache.clear();
            cache.putAll(definitions);
            allDefinitionsLoaded = true;
        }
        return cache.values().stream()
                .filter(def -> def != null)
                .filter(def -> processType.equals(def.predecessorProcessType)).toList();
    }

    /** Invalidate cached definitions and misses; subsequent lookups reload from the database. */
    public synchronized void clearCache() {
        cache.clear();
        allDefinitionsLoaded = false;
    }

    private ProcessDef toProcessDef(ProcessDefinition definition) {
        try {
            ProcessDef processDef = objectMapper.readValue(definition.definition, ProcessDef.class);
            processDef.processType = definition.processType;
            return processDef;
        } catch (JsonProcessingException e) {
            throw new ConfigurationException("1201", "Process definition " + definition.processType
                    + " cannot be processed. Error = " + e.getOriginalMessage());
        }
    }
}
