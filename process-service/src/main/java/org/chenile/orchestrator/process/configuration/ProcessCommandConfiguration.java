package org.chenile.orchestrator.process.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.core.event.EventProcessor;
import org.chenile.orchestrator.process.api.ProcessManager;
import org.chenile.orchestrator.process.configuration.dao.ProcessRepository;
import org.chenile.orchestrator.process.outbox.*;
import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.config.reader.IProcessConfigurator;
import org.chenile.orchestrator.process.service.outbox.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.List;

/** Commands are always available; only their delivery transport is optional. */
@Configuration
public class ProcessCommandConfiguration {
    // Retain the shared, command-specific JSON configuration without adding an application mapper bean.
    private final ObjectMapper mapper = new ObjectMapper();

    @Bean @Order(0) CreateSubProcessCommand createSubProcessCommand(
            ObjectProvider<ProcessOutboxRepository> repositories, ObjectProvider<ProcessManager> managers,
            PlatformTransactionManager transactionManager, ProcessRepository processes) {
        return new CreateSubProcessCommand(repositories.getIfAvailable(), managers, mapper, transactionManager, processes);
    }
    @Bean @Order(1) SignalParentCommand signalParentCommand(
            ObjectProvider<ProcessOutboxRepository> repositories, ObjectProvider<ProcessManager> managers,
            PlatformTransactionManager transactionManager) {
        return new SignalParentCommand(repositories.getIfAvailable(), managers, mapper, transactionManager);
    }
    @Bean @Order(2) EmitCompletedCommand emitCompletedCommand(
            ObjectProvider<ProcessOutboxRepository> repositories, ObjectProvider<ProcessManager> managers,
            PlatformTransactionManager transactionManager,
            org.chenile.orchestrator.process.configuration.dao.CompletionEventRepository history, EventProcessor events) {
        return new EmitCompletedCommand(repositories.getIfAvailable(), managers, mapper, transactionManager, history, events);
    }
    @Bean @Order(3) StartWorkerCommand startWorkerCommand(
            ObjectProvider<ProcessOutboxRepository> repositories, ObjectProvider<ProcessManager> managers,
            PlatformTransactionManager transactionManager, IProcessConfigurator configurator,
            ObjectProvider<WorkerStarter> starters) {
        return new StartWorkerCommand(repositories.getIfAvailable(), managers, mapper, transactionManager, configurator, starters);
    }

    @Bean OutboxCommandRegistry<ProcessTransition> processOutboxCommands(List<OutboxCommandLifecycle<ProcessTransition>> commands) {
        return new OutboxCommandRegistry<>(commands);
    }
}
