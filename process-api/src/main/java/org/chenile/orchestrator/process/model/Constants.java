package org.chenile.orchestrator.process.model;

/**
 * A centralized class for all constant string values (state and event IDs) used
 * in the process state machine. Using this class prevents typos and ensures
 * consistency between the XML configuration and the Java code.
 */
public final class Constants {

    private Constants() { /* Prevent instantiation */ }

    /**
     * Contains all the state IDs used in the process-states.xml.
     */
    public static final class States {
        private States() { /* Prevent instantiation */ }

        // --- Initialization States (where workers are started) ---
        public static final String INITIALIZING_SPLIT = "INITIALIZING_SPLIT";
        public static final String INITIALIZING_EXECUTION = "INITIALIZING_EXECUTION";
        public static final String INITIALIZING_AGGREGATION = "INITIALIZING_AGGREGATION";
        public static final String INITIALIZING_SUCCESSOR = "INITIALIZING_SUCCESSOR";

        // --- Active / Waiting States ---
        public static final String SPLIT_PENDING = "SPLIT_PENDING";
        public static final String EXECUTING = "EXECUTING";
        public static final String AGGREGATION_PENDING = "AGGREGATION_PENDING";

        // --- Final States ---
        public static final String PROCESSED = "PROCESSED";
        public static final String PROCESSED_WITH_ERRORS = "PROCESSED_WITH_ERRORS";
    }

    /**
     * Contains all the event IDs used in the process-states.xml.
     */
    public static final class Events {
        private Events() { /* Prevent instantiation */ }

        // --- Splitting Phase Events ---
        public static final String SPLIT_STARTED_EVENT = "splitStarted";
        public static final String SPLIT_INITIALIZATION_FAILED_EVENT = "splitInitializationFailed";
        public static final String ADD_SUBPROCESSES_EVENT = "addSubProcesses";
        public static final String SPLIT_COMPLETED_EVENT = "splitCompleted";
        public static final String SPLIT_FAILED_EVENT = "splitFailed";

        // --- Child Completion Events ---
        public static final String SUBPROCESS_COMPLETED_EVENT = "subProcessCompleted";
        public static final String SUBPROCESS_FAILED_EVENT = "subProcessFailed";

        // --- Execution Phase Events ---
        public static final String STATUS_UPDATE_EVENT = "statusUpdate";
        public static final String EXECUTION_STARTED_EVENT = "executionStarted";
        public static final String EXECUTION_INITIALIZATION_FAILED_EVENT = "executionInitializationFailed";
        public static final String EXECUTION_COMPLETED_EVENT = "executionCompleted";
        public static final String EXECUTION_FAILED_EVENT = "executionFailed";

        // --- Aggregation Phase Events ---
        public static final String AGGREGATION_STARTED_EVENT = "aggregationStarted";
        public static final String AGGREGATION_INITIALIZATION_FAILED_EVENT = "aggregationInitializationFailed";
        public static final String AGGREGATION_COMPLETED_EVENT = "aggregationCompleted";
        public static final String AGGREGATION_FAILED_EVENT = "aggregationFailed";

        // --- Successor/Chaining Events ---
        public static final String TRIGGER_SUCCESSOR_EVENT = "triggerSuccessor";
        public static final String TRIGGER_SUCCESSOR_FAILED_EVENT = "triggerSuccessorFailed";
        public static final String SUCCESSOR_CREATED_EVENT = "successorCreated";
        public static final String SUCCESSOR_FAILED_EVENT = "successorFailed";
    }
}
