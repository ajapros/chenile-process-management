package org.chenile.orchestrator.delegate;

import org.chenile.orchestrator.process.model.*;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.workflow.param.MinimalPayload;

public interface ProcessManagerClient {
    Process splitDone(String id, StartProcessingPayload payload);
    Process aggregationDone(String id, MinimalPayload payload);
    public Process statusUpdate(String id, StatusUpdatePayload payload);
    public Process doneSuccessfully(String id, DoneSuccessfullyPayload payload);
    public Process doneWithErrors(String id, DoneWithErrorsPayload payload);
}
