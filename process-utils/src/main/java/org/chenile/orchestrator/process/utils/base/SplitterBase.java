package org.chenile.orchestrator.process.utils.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.chenile.orchestrator.delegate.ProcessManagerClient;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.payload.StartProcessingPayload;
import org.chenile.orchestrator.process.model.payload.SubProcessPayload;
import org.chenile.orchestrator.process.utils.api.IWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.chenile.orchestrator.process.utils.ErrorsHelper.handleErrors;
import static org.chenile.orchestrator.process.utils.ProcessUtil.processProgressUpdate;

public abstract class SplitterBase<Input,ChildInput> implements IWorker<Input> {
    private final Logger logger = LoggerFactory.getLogger(SplitterBase.class);
    @Autowired ProcessManagerClient processManagerClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SplitterBase(){
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }
    @Override
    public void start(WorkerDto workerDto, Input input) {
        try {
            StartProcessingPayload payload = new StartProcessingPayload();
            payload.subProcesses = doStart(workerDto, input);
            processManagerClient.splitDone(workerDto.process.getId(), payload);
        }catch(Exception e){
            handleErrors(workerDto,e,processManagerClient, Constants.Events.SPLIT_DONE_WITH_ERRORS);
        }
    }

    /**
     * Implementations can call processManagerClient.splitUpdate(workerDto.process.getId(), batchPayload); as many times
     * as required. This would keep starting the other processes. Or else, it can return all the accumulated
     * processes in one shot and they will be sent to the process manager along with Split Done event.
     *
     * @param workerDto - The DTO
     * @param input - Input
     * @return the list of sub process payloads.
     */
    protected abstract List<SubProcessPayload> doStart(WorkerDto workerDto, Input input);

    protected void splitUpdate(String id, List<SubProcessPayload> subProcessPayloads){
        StartProcessingPayload payload = new StartProcessingPayload();
        payload.subProcesses = subProcessPayloads;
        processManagerClient.splitPartiallyDone(id, payload);
    }

    protected void progressUpdate(String processId, int percent){
        processProgressUpdate(processManagerClient,processId,percent);
    }

    protected SubProcessPayload makeSubProcessPayload(ChildInput child, String childProcessType){
        SubProcessPayload p = new SubProcessPayload();
        p.processType = childProcessType;
        try{
            p.args = objectMapper.writeValueAsString(child);
        }catch(Exception e){
            logger.error("Unable to construct args for process type = {}. ",childProcessType,e);
        }
        return p;
    }
}
