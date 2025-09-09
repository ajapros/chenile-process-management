package org.chenile.orchestrator.process.configuration;

import org.chenile.orchestrator.process.service.cmds.aggregation.AggregationCompletedAction;
import org.chenile.orchestrator.process.service.cmds.aggregation.AggregationFailedAction;
import org.chenile.orchestrator.process.service.cmds.common.SubProcessCompletedAction;
import org.chenile.orchestrator.process.service.cmds.common.SubProcessFailedAction;
import org.chenile.orchestrator.process.service.cmds.execution.ExecutionCompletedAction;
import org.chenile.orchestrator.process.service.cmds.execution.ExecutionFailedAction;
import org.chenile.orchestrator.process.service.cmds.execution.StatusUpdateAction;
import org.chenile.orchestrator.process.service.cmds.splitting.*;
import org.chenile.orchestrator.process.service.cmds.successor.SuccessorCreatedAction;
import org.chenile.orchestrator.process.service.cmds.successor.SuccessorFailedAction;
import org.chenile.orchestrator.process.service.cmds.successor.TriggerSuccessorAction;
import org.chenile.orchestrator.process.service.cmds.successor.TriggerSuccessorFailedAction;
import org.chenile.orchestrator.process.service.entry.NotifySuccessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProcessActionsConfiguration {

// --- Splitting Phase Actions ---

    @Bean
    public SplitStartedAction processSplitStarted() {
        return new SplitStartedAction();
    }

    @Bean
    public SplitInitializationFailedAction processSplitInitializationFailed() {
        return new SplitInitializationFailedAction();
    }

    @Bean
    public AddSubProcessesAction processAddSubProcesses() {
        return new AddSubProcessesAction();
    }

    @Bean
    public SplitCompletedAction processSplitCompleted() {
        return new SplitCompletedAction();
    }

    @Bean
    public SplitFailedAction processSplitFailed() {
        return new SplitFailedAction();
    }


    // --- Child Completion Actions ---

    @Bean
    public SubProcessCompletedAction processSubProcessCompleted() {
        return new SubProcessCompletedAction();
    }

    @Bean
    public SubProcessFailedAction processSubProcessFailed() {
        return new SubProcessFailedAction();
    }


    // --- Execution Phase Actions ---

    @Bean
    public StatusUpdateAction processStatusUpdate() {
        return new StatusUpdateAction();
    }

    @Bean
    public ExecutionCompletedAction processExecutionCompleted() {
        return new ExecutionCompletedAction();
    }

    @Bean
    public ExecutionFailedAction processExecutionFailed() {
        return new ExecutionFailedAction();
    }


    // --- Aggregation Phase Actions ---

    @Bean
    public AggregationCompletedAction processAggregationCompleted() {
        return new AggregationCompletedAction();
    }

    @Bean
    public AggregationFailedAction processAggregationFailed() {
        return new AggregationFailedAction();
    }


    // --- Successor / Chaining Phase Actions ---

    @Bean
    public TriggerSuccessorAction processTriggerSuccessor(NotifySuccessor notifySuccessor) {
        return new TriggerSuccessorAction(notifySuccessor);
    }

    @Bean
    public SuccessorCreatedAction processSuccessorCreated() {
        return new SuccessorCreatedAction();
    }

    @Bean
    public SuccessorFailedAction processSuccessorFailed() {
        return new SuccessorFailedAction();
    }

    @Bean
    public TriggerSuccessorFailedAction processTriggerSuccessorFailed() {
        return new TriggerSuccessorFailedAction();
    }
}
