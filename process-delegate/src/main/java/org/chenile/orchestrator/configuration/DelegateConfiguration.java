package org.chenile.orchestrator.configuration;

import org.chenile.orchestrator.delegate.ProcessManagerClient;
import org.chenile.orchestrator.delegate.ProcessManagerClientImpl;
import org.chenile.orchestrator.process.api.ProcessManager;
import org.chenile.proxy.builder.ProxyBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DelegateConfiguration {
    @Autowired
    ProxyBuilder proxyBuilder;
    @Value("${process.manager.base-url}")
    String baseUrl;
    @Bean
    public ProcessManagerClient processManagerClient(){
        return new ProcessManagerClientImpl();
    }

    @Bean public ProcessManager processServiceProxy(){
        return proxyBuilder.buildProxy(ProcessManager.class,
                "processService",null,
                baseUrl);
    }

}
