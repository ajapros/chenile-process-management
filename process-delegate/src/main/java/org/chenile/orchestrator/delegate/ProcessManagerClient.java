package org.chenile.orchestrator.delegate;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.*;

public interface ProcessManagerClient {
    Process splitUpdate(String id, StartProcessingPayload payload);
    Process splitDone(String id, StartProcessingPayload payload);
    Process aggregationDone(String id, AggregationDonePayload payload);
    public Process statusUpdate(String id, StatusUpdatePayload payload);
    public Process doneSuccessfully(String id, DoneSuccessfullyPayload payload);
    public Process doneWithErrors(String id, DoneWithErrorsPayload payload);
    public Process splitDoneWithErrors(String id, DoneWithErrorsPayload payload);
    public Process aggregationDoneWithErrors(String id, DoneWithErrorsPayload payload);
}
