package org.chenile.orchestrator.process.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.base.exception.ErrorNumException;
import org.chenile.base.response.ResponseMessage;
import org.chenile.orchestrator.delegate.ProcessManagerClient;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.payload.DoneWithErrorsPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

public class ErrorsHelper {
    private static final Logger logger = LoggerFactory.getLogger(ErrorsHelper.class);
    static final ObjectMapper objectMapper = new ObjectMapper();
    public static void handleErrors(WorkerDto workerDto, Throwable e,
                                    ProcessManagerClient processManagerClient,
                                    String eventId) {
        logger.error("Worker failure for processId={}, processType={}, workerType={}; reporting event={}",
                workerDto.process.getId(), workerDto.process.processType, workerDto.workerType, eventId, e);
        DoneWithErrorsPayload payload = new DoneWithErrorsPayload();
        if (e != null) {
            payload.exceptionMessage = e.getMessage();
            payload.stackTrace = stackTrace(e);
        }
        if (e instanceof ErrorNumException exception) {
            List<String> errors = new ArrayList<>();
            for(ResponseMessage rm: exception.getErrors()) {
                try {
                    errors.add(objectMapper.writeValueAsString(rm));
                } catch (Exception serializationFailure) {
                    logger.warn("Unable to serialize structured error while reporting processId={}",
                            workerDto.process.getId(), serializationFailure);
                }
            }
            payload.errors = errors;
        }
        processManagerClient.process(workerDto.process.getId(),eventId, payload);
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter output = new StringWriter();
        throwable.printStackTrace(new PrintWriter(output));
        return output.toString();
    }
}
