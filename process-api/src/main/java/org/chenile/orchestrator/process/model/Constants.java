package org.chenile.orchestrator.process.model;

public class Constants {
    public static final String SPLIT_PENDING_STATE = "SPLIT_PENDING";
    public static final String SUB_PROCESSES_PENDING_STATE = "SUB_PROCESSES_PENDING";
    public static final String AGGREGATION_PENDING_STATE = "AGGREGATION_PENDING";
    public static final String EXECUTING_STATE = "EXECUTING";
    public static final String PROCESSED_STATE = "PROCESSED";
    public static final String PROCESSED_WITH_ERRORS_STATE = "PROCESSED_WITH_ERRORS";
    public static final String SPLIT_DONE_EVENT = "splitDone";
    public static final String SPLIT_DONE_WITH_ERRORS_EVENT = "splitDoneWithErrors";
    public static final String AGGREGATION_DONE_WITH_ERRORS_EVENT = "aggregationDoneWithErrors";
    public static final String DONE_EVENT = "doneSuccessfully";
    public static final String STATUS_UPDATE_EVENT = "statusUpdate";
    public static final String DONE_WITH_ERRORS_EVENT = "doneWithErrors";
    public static final String SUB_PROCESS_DONE_EVENT = "subProcessDoneSuccessfully";
    public static final String SUB_PROCESS_DONE_WITH_ERRORS_EVENT = "subProcessDoneWithErrors";
    public static final String AGGREGATION_DONE_EVENT = "aggregationDone";
    public static final String SPLIT_PARTIALLY_DONE_EVENT = "splitPartiallyDone";
    public static final String ACTIVATE_DORMANT_EVENT = "activate";

    public static final String YES = "yes";
    public static final String NO = "no";

}
