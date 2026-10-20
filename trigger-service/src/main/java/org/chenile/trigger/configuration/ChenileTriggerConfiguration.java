package org.chenile.trigger.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.core.event.EventProcessor;
import org.chenile.orchestrator.process.configuration.ProcessOutboxConfiguration;
import org.chenile.orchestrator.process.outbox.ProcessOutboxRepository;
import org.chenile.trigger.*;
import org.chenile.trigger.configuration.controller.TriggerController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@Import({TriggerController.class, ProcessOutboxConfiguration.class})
public class ChenileTriggerConfiguration {
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  @Bean
  public DispatchTriggerCommand dispatchTriggerCommand(
      ProcessOutboxRepository outbox, EventProcessor events) {
    return new DispatchTriggerCommand(outbox, events, mapper);
  }

  @Bean
  public EventTriggerService triggerService(
      ProcessOutboxRepository outbox,
      DispatchTriggerCommand command,
      PlatformTransactionManager manager) {
    return new EventTriggerService(outbox, command, mapper, manager);
  }
}
