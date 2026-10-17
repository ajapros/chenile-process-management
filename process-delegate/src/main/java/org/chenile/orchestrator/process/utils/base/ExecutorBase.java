package org.chenile.orchestrator.process.utils.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.chenile.orchestrator.delegate.ProcessManagerClient;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.payload.DoneSuccessfullyPayload;
import org.chenile.orchestrator.process.utils.api.IWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import static org.chenile.orchestrator.process.utils.ErrorsHelper.handleErrors;
import static org.chenile.orchestrator.process.utils.ProcessUtil.processProgressUpdate;

public abstract class ExecutorBase<Input,Output> implements IWorker<Input> {
    Logger logger = LoggerFactory.getLogger(ExecutorBase.class);
    @Autowired
    ProcessManagerClient processManagerClient;
    protected final ObjectMapper objectMapper = new ObjectMapper();

    public ExecutorBase(){
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS,false);
    }
    @Override
    public final void start(WorkerDto workerDto, Input input) {
        try {
            logger.info("Worker type = {} input = {}",workerDto.workerType,input);
            Output output = doStart(workerDto, input);
            String outString = objectMapper.writeValueAsString(output);
            DoneSuccessfullyPayload payload = new DoneSuccessfullyPayload();
            payload.output = outString;
            processManagerClient.doneSuccessfully(workerDto.process.getId(), payload);
        } catch (Exception e) {
            handleErrors(workerDto,e,processManagerClient, Constants.Events.DONE_WITH_ERRORS);
        }
    }

    /**
     * Convert input to output.
     * Throw an exception in cases of errors. Exception must extend ErrorNumException<br/>
     * Call {@link #progressUpdate(String,int)} progressUpdate} in case you want to update progress.
     * @param workerDto - The Worker DTO to work with
     * @param input - Converted into a Java object from JSON.
     * @return the output that will be sent back to the Process.
     */
    protected abstract Output doStart(WorkerDto workerDto,Input input);
    protected void progressUpdate(String processId, int percent){
        processProgressUpdate(processManagerClient,processId,percent);
    }
}
