package org.chenile.orchestrator.process.utils.base;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.delegate.ProcessManagerClient;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.payload.DoneSuccessfullyPayload;
import org.chenile.orchestrator.process.utils.api.IWorker;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.chenile.orchestrator.process.utils.ErrorsHelper.handleErrors;
import static org.chenile.orchestrator.process.utils.ProcessUtil.processProgressUpdate;

/**
 * Unify all the children's outputs and make one consolidated output for the current process
 * @param <Input> - The process.input
 * @param <Output> - The process.output
 */
public abstract class AggregatorBase<Input,Output> implements IWorker<Input> {
    @Autowired ProcessManagerClient processManagerClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Override
    public void start(WorkerDto workerDto, Input input) {
        try {
            List<Process> processes = processManagerClient.getSubProcesses(workerDto.process.getId(),false);
            Output out = null;
            for (Process p: processes){
                if (p.getId().equals(workerDto.process.getId())){
                    continue;
                }
                out = doStart(out,input,workerDto,p);
            }
            DoneSuccessfullyPayload doneSuccessfullyPayload = new DoneSuccessfullyPayload();
            doneSuccessfullyPayload.output = objectMapper.writeValueAsString(doneSuccessfullyPayload);
        } catch (JsonProcessingException e) {
            handleErrors(workerDto,e,processManagerClient, Constants.Events.DONE_WITH_ERRORS);
        }
    }

    protected void progressUpdate(String processId, int percent){
        processProgressUpdate(processManagerClient,processId,percent);
    }
    protected abstract Output doStart(Output out, Input input, WorkerDto workerDto, Process p) ;

}
