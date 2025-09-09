package org.chenile.orchestrator.delegate;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.aggregation.AggregationCompletedPayload;
import org.chenile.orchestrator.process.model.payload.aggregation.AggregationFailedPayload;
import org.chenile.orchestrator.process.model.payload.aggregation.AggregationInitializationFailedPayload;
import org.chenile.orchestrator.process.model.payload.aggregation.AggregationStartedPayload;
import org.chenile.orchestrator.process.model.payload.common.SubProcessCompletedPayload;
import org.chenile.orchestrator.process.model.payload.common.SubProcessFailedPayload;
import org.chenile.orchestrator.process.model.payload.execution.*;
import org.chenile.orchestrator.process.model.payload.splitting.*;
import org.chenile.orchestrator.process.model.payload.successor.SuccessorCreatedPayload;
import org.chenile.orchestrator.process.model.payload.successor.SuccessorFailedPayload;
import org.chenile.orchestrator.process.model.payload.successor.TriggerSuccessorFailedPayload;
import org.chenile.orchestrator.process.model.payload.successor.TriggerSuccessorPayload;

/**
 * The client API for workers to interact with the Process Manager.
 * Each method corresponds to a specific event in the process state machine.
 */
public interface ProcessManagerClient {

    // ===================================================================
    //  Splitting Phase Events (sent by Splitter worker)
    // ===================================================================

    /**
     * Sent by the splitter worker to confirm it has begun. Can contain the first batch of sub-processes.
     */
    Process splitStarted(String id, SplitStartedPayload payload);

    /**
     * Sent if the splitter worker fails immediately during its initialization.
     */
    Process splitInitializationFailed(String id, SplitInitializationFailedPayload payload);

    /**
     * Adds a batch of sub-processes during an asynchronous split. Can be called multiple times.
     */
    Process addSubProcesses(String id, AddSubProcessesPayload payload);

    /**
     * Signals that the splitting process is complete. Can optionally contain the final batch.
     */
    Process splitCompleted(String id, SplitCompletedPayload payload);

    /**
     * Signals that the splitter worker has failed during its main processing.
     */
    Process splitFailed(String id, SplitFailedPayload payload);


    // ===================================================================
    //  Child to Parent Notification Events (sent by any completed child)
    // ===================================================================

    /**
     * A generic notification sent from a child process to its parent when it has completed successfully.
     */
    Process subProcessCompleted(String id, SubProcessCompletedPayload payload);

    /**
     * A generic notification sent from a child process to its parent when it has completed with errors.
     */
    Process subProcessFailed(String id, SubProcessFailedPayload payload);


    // ===================================================================
    //  Execution Phase Events (sent by Executor worker)
    // ===================================================================

    /**
     * Sent by the executor worker to confirm it has begun its work.
     */
    Process executionStarted(String id, ExecutionStartedPayload payload);

    /**
     * Sent if the executor worker fails immediately during its initialization.
     */
    Process executionInitializationFailed(String id, ExecutionInitializationFailedPayload payload);

    /**
     * Provides an intermediate status update from a running executor.
     */
    Process statusUpdate(String id, StatusUpdatePayload payload);

    /**
     * Signals that a leaf executor has finished successfully.
     */
    Process executionCompleted(String id, ExecutionCompletedPayload payload);

    /**
     * Signals that a leaf executor has finished with a failure.
     */
    Process executionFailed(String id, ExecutionFailedPayload payload);


    // ===================================================================
    //  Aggregation Phase Events (sent by Aggregator worker)
    // ===================================================================

    /**
     * Sent by the aggregator worker to confirm it has begun its work.
     */
    Process aggregationStarted(String id, AggregationStartedPayload payload);

    /**
     * Sent if the aggregator worker fails immediately during its initialization.
     */
    Process aggregationInitializationFailed(String id, AggregationInitializationFailedPayload payload);

    /**
     * Signals that the aggregator has finished successfully.
     */
    Process aggregationCompleted(String id, AggregationCompletedPayload payload);

    /**
     * Signals that the aggregator worker has failed.
     */
    Process aggregationFailed(String id, AggregationFailedPayload payload);


    // ===================================================================
    //  Successor / Chaining Phase Events
    // ===================================================================

    /**
     * Sent by a completed child's SUCCESSOR_TRIGGER worker to its parent, requesting
     * the creation of a sequential successor process.
     */
    Process triggerSuccessor(String id, TriggerSuccessorPayload payload);

    /**
     * Sent by the SUCCESSOR_TRIGGER worker to its own process if it fails to send the 'triggerSuccessor' event.
     */
    Process triggerSuccessorFailed(String id, TriggerSuccessorFailedPayload payload);

    /**
     * A confirmation sent from a parent back to a child, confirming that
     * the requested successor process has been successfully created.
     */
    Process successorCreated(String id, SuccessorCreatedPayload payload);

    /**
     * Sent from a parent back to a child if the creation of the successor failed.
     */
    Process successorFailed(String id, SuccessorFailedPayload payload);
}




