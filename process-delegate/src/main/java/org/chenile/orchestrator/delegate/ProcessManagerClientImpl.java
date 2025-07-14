package org.chenile.orchestrator.delegate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.process.model.*;
import org.chenile.orchestrator.process.model.Process;
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

    @Override
    public Process splitDone(String id, StartProcessingPayload payload) {
        return process(id, Constants.SPLIT_DONE, payload);

    }

    @Override
    public Process aggregationDone(String id, MinimalPayload payload) {
        return process(id, Constants.AGGREGATION_DONE, payload);
    }

    @Override
    public Process statusUpdate(String id, StatusUpdatePayload payload) {
        return process(id, Constants.DONE_EVENT, payload);
    }

    @Override
    public Process doneSuccessfully(String id, DoneSuccessfullyPayload payload) {
        return process(id, Constants.DONE_EVENT, payload);
    }

    @Override
    public Process doneWithErrors(String id, DoneWithErrorsPayload payload) {
        return process(id, Constants.DONE_WITH_ERRORS_EVENT, payload);
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
                throw new RuntimeException("Master with error code "+response.getStatusCode());
            }

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing payload to JSON", e);
        }
    }
}
