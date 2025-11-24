package org.chenile.orchestrator.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.pubsub.ChenilePub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

public class QueueBasedProcessStarter implements WorkerStarter{
    private final Logger logger = LoggerFactory.getLogger(QueueBasedProcessStarter.class);
    public String topicNameTemplate;
    @Autowired ChenilePub chenilePub;
    ObjectMapper objectMapper = new ObjectMapper();
    @Override
    public void start(WorkerDto workerDto) {
        String topic = "topic1";
        logger.info("start(): Posting the following message to topic " + topic + " WorkerDto.type is {}", workerDto.workerType);
        Map<String,Object> props = new HashMap<>();
        try{
            String s = objectMapper.writeValueAsString(workerDto);
            chenilePub.asyncPublish(topic,s,props);
        }catch(Exception ignore){}

    }
}
