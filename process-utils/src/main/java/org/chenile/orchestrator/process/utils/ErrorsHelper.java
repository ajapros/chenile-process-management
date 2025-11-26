package org.chenile.orchestrator.process.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.base.exception.ErrorNumException;
import org.chenile.base.response.ResponseMessage;
import org.chenile.orchestrator.delegate.ProcessManagerClient;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.payload.DoneWithErrorsPayload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ErrorsHelper {
    static final ObjectMapper objectMapper = new ObjectMapper();
    public static void handleErrors(WorkerDto workerDto, Throwable e,
                                    ProcessManagerClient processManagerClient,
                                    String eventId) {
        DoneWithErrorsPayload payload = new DoneWithErrorsPayload();
        if (e != null) {
            payload.exceptionMessage = e.getMessage();
            payload.stackTrace = Arrays.toString(e.getStackTrace());
        }
        if (e instanceof ErrorNumException exception) {
            List<String> errors = new ArrayList<>();
            for(ResponseMessage rm: exception.getErrors()) {
                try {
                    errors.add(objectMapper.writeValueAsString(rm));
                } catch (Exception ignore) {}
            }
            payload.errors = errors;
        }
        processManagerClient.process(workerDto.process.getId(),eventId, payload);
    }
}
