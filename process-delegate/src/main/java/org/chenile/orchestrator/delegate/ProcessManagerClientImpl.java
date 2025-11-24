package org.chenile.orchestrator.delegate;

import org.chenile.orchestrator.process.api.ProcessManager;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.*;
import org.chenile.workflow.api.StateEntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestTemplate;

import java.util.List;

public class ProcessManagerClientImpl implements ProcessManagerClient {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    @Autowired  @Qualifier("processServiceProxy") private ProcessManager processServiceProxy;

    @Override
    public Process start(Process process) {
        return callCreate(process);
    }

    @Override
    public Process splitUpdate(String id, StartProcessingPayload payload) {
        return process(id, Constants.Events.SPLIT_UPDATE, payload);
    }

    @Override
    public Process splitDone(String id, StartProcessingPayload payload) {
        return process(id, Constants.Events.SPLIT_DONE, payload);
    }

    @Override
    public Process aggregationDone(String id, AggregationDonePayload payload) {
        return process(id, Constants.Events.AGGREGATION_DONE, payload);
    }

    @Override
    public Process statusUpdate(String id, StatusUpdatePayload payload) {
        return process(id, Constants.Events.STATUS_UPDATE, payload);
    }

    @Override
    public Process doneSuccessfully(String id, DoneSuccessfullyPayload payload) {
        return process(id, Constants.Events.DONE_SUCCESSFULLY, payload);
    }

    @Override
    public Process doneWithErrors(String id, DoneWithErrorsPayload payload) {
        return process(id, Constants.Events.DONE_WITH_ERRORS, payload);
    }

    @Override
    public Process splitDoneWithErrors(String id, DoneWithErrorsPayload payload) {
        return process(id, Constants.Events.SPLIT_DONE_WITH_ERRORS, payload);
    }

    @Override
    public Process aggregationDoneWithErrors(String id, DoneWithErrorsPayload payload) {
        return process(id, Constants.Events.AGGREGATION_DONE_WITH_ERRORS, payload);
    }

    private Process callCreate(Process process) {
        return processServiceProxy.create(process).getMutatedEntity();
    }

    public Process process(String id,  String event, Object payload) {
        return processServiceProxy.processById(id,event,payload).getMutatedEntity();
    }

    @Override
    public List<Process> getSubProcesses(String id, boolean recursive) {
        return processServiceProxy.getSubProcesses(id,recursive);
    }
}
