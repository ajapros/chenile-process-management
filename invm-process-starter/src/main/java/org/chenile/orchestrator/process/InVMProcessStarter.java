package org.chenile.orchestrator.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.core.event.EventProcessor;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class InVMProcessStarter implements WorkerStarter{
    private final Logger logger = LoggerFactory.getLogger(InVMProcessStarter.class);
    @Autowired EventProcessor eventProcessor;
    ObjectMapper objectMapper = new ObjectMapper();
    @Override
    public void start(WorkerDto workerDto) {
        String topic = "topic1";
        logger.info("start(): Posting the following message to topic " + topic + " WorkerDto.type is {}", workerDto.workerType);
        try{
            String s = objectMapper.writeValueAsString(workerDto);
            eventProcessor.handleEvent(topic,s);
        }catch(Exception ignore){
            ignore.printStackTrace();
        }

    }
}
