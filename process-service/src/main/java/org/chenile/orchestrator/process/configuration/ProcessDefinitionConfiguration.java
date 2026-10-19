package org.chenile.orchestrator.process.configuration;

import org.chenile.orchestrator.process.api.ProcessDefinitionService;
import org.chenile.orchestrator.process.config.reader.ProcessConfigurator;
import org.chenile.orchestrator.process.configuration.controller.ProcessDefinitionController;
import org.chenile.orchestrator.process.configuration.dao.ProcessDefinitionRepository;
import org.chenile.orchestrator.process.service.defs.ProcessDefinitionServiceImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;

/** Available independently of the optional management API. Persistence scans belong to the host. */
@Configuration
@Import(ProcessDefinitionController.class)
public class ProcessDefinitionConfiguration {
    @Bean(name = {"_processDefinitionService_", "processDefinitionService"})
    public ProcessDefinitionService processDefinitionService(ProcessConfigurator configurator,
            ObjectProvider<ProcessDefinitionRepository> repositories) {
        return new ProcessDefinitionServiceImpl(configurator, repositories.getIfAvailable());
    }
}
