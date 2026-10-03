package org.chenile.orchestrator.process.configuration;

import org.chenile.orchestrator.process.config.reader.ProcessConfigurator;
import org.chenile.orchestrator.process.configuration.dao.*;
import org.chenile.orchestrator.process.configuration.management.*;
import org.chenile.trigger.TriggerService;
import org.chenile.trigger.cron.*;
import org.chenile.trigger.store.TriggerExecutionRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;

@Configuration
@ConditionalOnProperty(name = "chenile.process.management.enabled", havingValue = "true")
public class ProcessManagementConfiguration {
    @Bean ProcessManagementService processManagementService(ProcessRepository processes, CompletionEventRepository completions,
            ProcessDefinitionRepository definitions, ProcessConfigurator configurator, CrontabRepository crontabs,
            ObjectProvider<CrontabService> cronServices, TriggerExecutionRepository executions, TriggerService triggers) {
        return new ProcessManagementService(processes, completions, definitions, configurator, crontabs, cronServices, executions, triggers);
    }
    @Bean FilterRegistrationBean<ManagementApiFilter> managementApiFilter(
            @Value("${chenile.process.management.api-key:}") String key,
            @Value("${chenile.process.management.protect-all-http-routes:false}") boolean protectAllRoutes) {
        var registration = new FilterRegistrationBean<>(new ManagementApiFilter(key));
        registration.addUrlPatterns(protectAllRoutes ? "/*" : "/process-management/api/*");
        registration.setOrder(-100);
        return registration;
    }
}
