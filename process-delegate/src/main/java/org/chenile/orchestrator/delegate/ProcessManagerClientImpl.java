package org.chenile.orchestrator.delegate;

import org.chenile.orchestrator.process.model.*;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.workflow.param.MinimalPayload;

public class ProcessManagerClientImpl implements ProcessManagerClient{
    @Override
    public Process splitDone(String id, StartProcessingPayload payload) {
        return null;
    }

    @Override
    public Process aggregationDone(String id, MinimalPayload payload) {
        return null;
    }

    @Override
    public Process statusUpdate(String id, StatusUpdatePayload payload) {
        return null;
    }

    @Override
    public Process doneSuccessfully(String id, DoneSuccessfullyPayload payload) {
        return null;
    }

    @Override
    public Process doneWithErrors(String id, DoneWithErrorsPayload payload) {
        return null;
    }
}
