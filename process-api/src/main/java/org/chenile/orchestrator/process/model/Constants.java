package org.chenile.orchestrator.process.model;

public final class Constants {
    private Constants() { /* Prevent instantiation */ }

    public static final class States {
        private States() { /* Prevent instantiation */ }
        // --- Core States ---
        public static final String DORMANT = "DORMANT";
        public static final String SUB_PROCESSES_PENDING = "SUB_PROCESSES_PENDING";
        public static final String SPLIT_PENDING = "SPLIT_PENDING";
        public static final String AGGREGATION_PENDING = "AGGREGATION_PENDING";
        public static final String EXECUTING = "EXECUTING";

        // --- Final States ---
        public static final String PROCESSED = "PROCESSED";
        public static final String PROCESSED_WITH_ERRORS = "PROCESSED_WITH_ERRORS";

    }

    public static final class Events {
        private Events() { /* Prevent instantiation */ }

        // --- Primary Actions & Updates ---
        public static final String ACTIVATE = "activate";
        public static final String SPLIT_PARTIALLY_DONE = "splitPartiallyDone";
        public static final String STATUS_UPDATE = "statusUpdate";

        // --- Completion Events ---
        public static final String SPLIT_DONE = "splitDone";
        public static final String AGGREGATION_DONE = "aggregationDone";
        public static final String DONE_SUCCESSFULLY = "doneSuccessfully";
        public static final String SUB_PROCESS_DONE_SUCCESSFULLY = "subProcessDoneSuccessfully";

        // --- Error Events ---
        public static final String SPLIT_DONE_WITH_ERRORS = "splitDoneWithErrors";
        public static final String AGGREGATION_DONE_WITH_ERRORS = "aggregationDoneWithErrors";
        public static final String DONE_WITH_ERRORS = "doneWithErrors";
        public static final String SUB_PROCESS_DONE_WITH_ERRORS = "subProcessDoneWithErrors";

        // --- Conditional Event IDs (not true events, but used in 'if' transitions) ---
        public static final String YES = "yes";
        public static final String NO = "no";
    }
}