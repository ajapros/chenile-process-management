package org.chenile.orchestrator.process.utils.base;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.chenile.orchestrator.delegate.ProcessManagerClient;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.payload.AggregationDonePayload;
import org.chenile.orchestrator.process.utils.ProcessUtil;
import org.chenile.orchestrator.process.utils.api.IWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.chenile.orchestrator.process.utils.ErrorsHelper.handleErrors;
import static org.chenile.orchestrator.process.utils.ProcessUtil.deriveType;
import static org.chenile.orchestrator.process.utils.ProcessUtil.processProgressUpdate;

/**
 * Unify all the children's outputs and make one consolidated output for the current process
 * @param <Input> - The process.input
 * @param <Output> - The process.output
 */
public abstract class AggregatorBase<Input,Output,ChildOutput> implements IWorker<Input> {
    private final Logger logger = LoggerFactory.getLogger(AggregatorBase.class);
    @Autowired ProcessManagerClient processManagerClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    public AggregatorBase(){
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS,false);
    }
    @Override
    public void start(WorkerDto workerDto, Input input) {
        try {
            logger.info("Input = {}",input);
            List<Process> processes = processManagerClient.getSubProcesses(workerDto.process.getId(),false);
            Output out = null;
            for (Process p: processes){
                if (p.getId().equals(workerDto.process.getId())){
                    continue;
                }
                ChildOutput childOutput = null;
                if (p.output != null){
                    ProcessUtil.TypeInfo<ChildOutput> type = deriveType(this,"doStart",2);
                    logger.debug("Class: {} Child output type is {}. p.output is {}. process type {}",
                            getClass().getName(),type.type.getType(),p.output,p.processType);
                    if (type != null && !type.isString)
                        childOutput = objectMapper.readValue(p.output,type.type);
                }
                out = doStart(out,input,childOutput,workerDto,p);
            }
            AggregationDonePayload aggregationDonePayload = new AggregationDonePayload();
            aggregationDonePayload.output = objectMapper.writeValueAsString(out);
            processManagerClient.aggregationDone(workerDto.process.getId(),aggregationDonePayload);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            handleErrors(workerDto,e,processManagerClient, Constants.Events.AGGREGATION_DONE_WITH_ERRORS);
        }
    }

    protected void progressUpdate(String processId, int percent){
        processProgressUpdate(processManagerClient,processId,percent);
    }
    protected abstract Output doStart(Output out, Input input, ChildOutput childOutput,WorkerDto workerDto, Process p) ;

}
