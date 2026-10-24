package org.chenile.orchestrator.process.worker;

import org.chenile.core.context.ChenileExchange;
import org.chenile.core.model.ChenileServiceDefinition;
import org.chenile.core.service.ServiceSelector;
import org.springframework.stereotype.Component;

/** Selects the process-specific listener bean using the same name convention as BatchServiceBase. */
@Component("processWorkServiceSelector")
public class ProcessWorkServiceSelector implements ServiceSelector {
  @Override
  public String selectServiceReferenceName(
      ChenileExchange exchange, ChenileServiceDefinition serviceDefinition) {
    return WorkerListenerNames.beanName(exchange);
  }
}
