package org.chenile.orchestrator.configuration;

import org.chenile.core.context.PopulateContextContainer;
import org.chenile.orchestrator.delegate.ProcessManagerClient;
import org.chenile.orchestrator.delegate.ProcessManagerClientImpl;
import org.chenile.orchestrator.process.api.ProcessManager;
import org.chenile.orchestrator.process.worker.ProcessWorkBodyTypeSelector;
import org.chenile.orchestrator.process.worker.ProcessWorkServiceFallback;
import org.chenile.orchestrator.process.worker.ProcessWorkServiceSelector;
import org.chenile.orchestrator.process.workers.WorkListenerService;
import org.chenile.proxy.builder.ProxyBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({ProcessWorkServiceSelector.class, ProcessWorkBodyTypeSelector.class})
public class DelegateConfiguration {
  @Autowired ProxyBuilder proxyBuilder;
  @Autowired PopulateContextContainer populateContextContainer;

  @Bean
  public ProcessManagerClient processManagerClient() {
    return new ProcessManagerClientImpl();
  }

  @Bean
  public ProcessManager processServiceProxy() {
    return proxyBuilder.buildProxy(
        ProcessManager.class, "processService", populateContextContainer);
  }

  @Bean("_processWorkService_")
  public WorkListenerService<Object> processWorkService() {
    return new ProcessWorkServiceFallback();
  }
}
