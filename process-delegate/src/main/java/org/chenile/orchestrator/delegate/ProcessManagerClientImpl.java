package org.chenile.orchestrator.delegate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.process.model.*;
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
import org.chenile.workflow.dto.StateEntityServiceResponse;
import org.chenile.workflow.param.MinimalPayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;

public class ProcessManagerClientImpl implements ProcessManagerClient {

    private final RestTemplate restTemplate;

    @Value("${process.manager.base-url}")
    String baseUrl;

    public ProcessManagerClientImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }


    // --- Splitting Phase ---

    @Override
    public Process splitStarted(String id, SplitStartedPayload payload) {
        return process(id, Constants.Events.SPLIT_STARTED_EVENT, payload);
    }

    @Override
    public Process splitInitializationFailed(String id, SplitInitializationFailedPayload payload) {
        return process(id, Constants.Events.SPLIT_INITIALIZATION_FAILED_EVENT, payload);
    }

    @Override
    public Process addSubProcesses(String id, AddSubProcessesPayload payload) {
        return process(id, Constants.Events.ADD_SUBPROCESSES_EVENT, payload);
    }

    @Override
    public Process splitCompleted(String id, SplitCompletedPayload payload) {
        return process(id, Constants.Events.SPLIT_COMPLETED_EVENT, payload);
    }

    @Override
    public Process splitFailed(String id, SplitFailedPayload payload) {
        return process(id, Constants.Events.SPLIT_FAILED_EVENT, payload);
    }

    // --- Child to Parent Notification ---

    @Override
    public Process subProcessCompleted(String id, SubProcessCompletedPayload payload) {
        return process(id, Constants.Events.SUBPROCESS_COMPLETED_EVENT, payload);
    }

    @Override
    public Process subProcessFailed(String id, SubProcessFailedPayload payload) {
        return process(id, Constants.Events.SUBPROCESS_FAILED_EVENT, payload);
    }

    // --- Execution Phase ---

    @Override
    public Process executionStarted(String id, ExecutionStartedPayload payload) {
        return process(id, Constants.Events.EXECUTION_STARTED_EVENT, payload);
    }

    @Override
    public Process executionInitializationFailed(String id, ExecutionInitializationFailedPayload payload) {
        return process(id, Constants.Events.EXECUTION_INITIALIZATION_FAILED_EVENT, payload);
    }

    @Override
    public Process statusUpdate(String id, StatusUpdatePayload payload) {
        return process(id, Constants.Events.STATUS_UPDATE_EVENT, payload);
    }

    @Override
    public Process executionCompleted(String id, ExecutionCompletedPayload payload) {
        return process(id, Constants.Events.EXECUTION_COMPLETED_EVENT, payload);
    }

    @Override
    public Process executionFailed(String id, ExecutionFailedPayload payload) {
        return process(id, Constants.Events.EXECUTION_FAILED_EVENT, payload);
    }

    // --- Aggregation Phase ---

    @Override
    public Process aggregationStarted(String id, AggregationStartedPayload payload) {
        return process(id, Constants.Events.AGGREGATION_STARTED_EVENT, payload);
    }

    @Override
    public Process aggregationInitializationFailed(String id, AggregationInitializationFailedPayload payload) {
        return process(id, Constants.Events.AGGREGATION_INITIALIZATION_FAILED_EVENT, payload);
    }

    @Override
    public Process aggregationCompleted(String id, AggregationCompletedPayload payload) {
        return process(id, Constants.Events.AGGREGATION_COMPLETED_EVENT, payload);
    }

    @Override
    public Process aggregationFailed(String id, AggregationFailedPayload payload) {
        return process(id, Constants.Events.AGGREGATION_FAILED_EVENT, payload);
    }

    // --- Successor / Chaining Phase ---

    @Override
    public Process triggerSuccessor(String id, TriggerSuccessorPayload payload) {
        return process(id, Constants.Events.TRIGGER_SUCCESSOR_EVENT, payload);
    }

    @Override
    public Process triggerSuccessorFailed(String id, TriggerSuccessorFailedPayload payload) {
        return process(id, Constants.Events.TRIGGER_SUCCESSOR_FAILED_EVENT, payload);
    }

    @Override
    public Process successorCreated(String id, SuccessorCreatedPayload payload) {
        return process(id, Constants.Events.SUCCESSOR_CREATED_EVENT, payload);
    }

    @Override
    public Process successorFailed(String id, SuccessorFailedPayload payload) {
        return process(id, Constants.Events.SUCCESSOR_FAILED_EVENT, payload);
    }

    private Process process(String id, String event, Object payload) {
        String url = baseUrl + "/process/" + id + "/" + event;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ObjectMapper objectMapper = new ObjectMapper();

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);

            ResponseEntity<StateEntityServiceResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    request,
                    StateEntityServiceResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                StateEntityServiceResponse<Process> responseBody = response.getBody();
                return responseBody.getMutatedEntity();
            } else {
                throw new RuntimeException("Master with error code " + response.getStatusCode());
            }

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing payload to JSON", e);
        }
    }


}
