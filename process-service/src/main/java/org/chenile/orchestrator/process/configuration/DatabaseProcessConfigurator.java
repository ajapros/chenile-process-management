package org.chenile.orchestrator.process.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.base.exception.ConfigurationException;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.config.reader.ProcessConfigurator;
import org.chenile.orchestrator.process.configuration.dao.ProcessDefinitionRepository;
import org.chenile.orchestrator.process.configuration.model.ProcessDefinition;

/** Resolves process definitions from the {@code process_definition} table. */
public class DatabaseProcessConfigurator extends ProcessConfigurator {
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final ObjectMapper objectMapper;

    public DatabaseProcessConfigurator(ProcessDefinitionRepository processDefinitionRepository) {
        this(processDefinitionRepository, new ObjectMapper());
    }

    DatabaseProcessConfigurator(ProcessDefinitionRepository processDefinitionRepository, ObjectMapper objectMapper) {
        this.processDefinitionRepository = processDefinitionRepository;
        this.objectMapper = objectMapper;
    }

    public ProcessDef findByName(String name) {
        return processDefinitionRepository.findById(name)
                .map(this::toProcessDef)
                .orElse(null);
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
