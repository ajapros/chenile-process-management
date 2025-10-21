package org.chenile.orchestrator.process.service.defs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.config.model.Processes;
import org.chenile.base.exception.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Reads additional configuration about a process based on its process type.<br/>
 * This information can be used by other classes to make some choices. For example, the
 * Workflow service for Process uses this to default the leaf node attribute in the {@link org.chenile.orchestrator.process.model.Process}
 * model. <br/>
 * This also provides additional information to start a worker.
 */
public class ProcessConfigurator {
    private static final Logger logger = LoggerFactory.getLogger(ProcessConfigurator.class);

    public Processes processes = new Processes();
    private final ObjectMapper objectMapper;

    public ProcessConfigurator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void read(String file) {
        logger.debug("Loading process configuration from: {}", file);

        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(file)) {
            if (stream == null) {
                throw new ConfigurationException(1201,
                        "Configuration file '" + file + "' not found in classpath.");
            }

            processes = objectMapper.readValue(stream, Processes.class);

            if (processes.processMap == null || processes.processMap.isEmpty()) {
                logger.warn("No process definitions found in file: {}", file);
                return;
            }

            for (Map.Entry<String, ProcessDef> entry : processes.processMap.entrySet()) {
                String key = entry.getKey();
                ProcessDef processDef = entry.getValue();
                processDef.processType = key;
            }

            logger.info("Loaded {} process definitions from {}",
                    processes.processMap.size(), file);

        } catch (IOException e) {
            throw new ConfigurationException(1200,
                    "Error reading configuration file '" + file + "': " + e.getMessage(), e);
        }
    }
}
