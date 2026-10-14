package org.chenile.orchestrator.process.configuration;

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
    @Bean ProcessCommandSupport processCommandSupport(ObjectProvider<ProcessOutboxRepository> repositories,
            ObjectProvider<ProcessManager> managers, ProcessRepository processes,
            EventProcessor events, PlatformTransactionManager transactionManager) {
        return new ProcessCommandSupport(repositories.getIfAvailable(), managers, processes, events, transactionManager);
    }

    @Bean @Order(0) CreateSubProcessCommand createSubProcessCommand(ProcessCommandSupport support) { return new CreateSubProcessCommand(support); }
    @Bean @Order(1) SignalParentCommand signalParentCommand(ProcessCommandSupport support) { return new SignalParentCommand(support); }
    @Bean @Order(2) EmitCompletedCommand emitCompletedCommand(ProcessCommandSupport support,
            org.chenile.orchestrator.process.configuration.dao.CompletionEventRepository history) {
        return new EmitCompletedCommand(support, history);
    }
    @Bean @Order(3) StartWorkerCommand startWorkerCommand(ProcessCommandSupport support,
            IProcessConfigurator configurator, ObjectProvider<WorkerStarter> starters) {
        return new StartWorkerCommand(support, configurator, starters);
    }

    @Bean OutboxCommandRegistry<ProcessTransition> processOutboxCommands(List<OutboxCommandLifecycle<ProcessTransition>> commands) {
        return new OutboxCommandRegistry<>(commands);
    }
}
