package org.chenile.orchestrator.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.chenile.base.exception.ConfigurationException;
import org.chenile.base.exception.ServerException;
import org.chenile.core.context.ContextContainer;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.pubsub.ChenilePub;
import org.chenile.utils.str.StrSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class QueueBasedProcessStarter implements WorkerStarter {
  private final Logger logger = LoggerFactory.getLogger(QueueBasedProcessStarter.class);
  public String topicNameTemplate;
  @Autowired ChenilePub chenilePub;
  ObjectMapper objectMapper = new ObjectMapper();

  public QueueBasedProcessStarter(String topicNameTemplate) {
    this.topicNameTemplate = topicNameTemplate;
  }

  public QueueBasedProcessStarter() {}

  private String topic(WorkerDto workerDto) {
    if (topicNameTemplate != null) {
      return resolveTopic(topicNameTemplate);
    }
    String topic = workerDto.execDef.get("queue");
    if (topic != null) {
      return topic;
    }
    throw new ConfigurationException("NO_TOPIC_SPECIFIED", "Cannot resolve topic");
  }

  private String resolveTopic(String topicNameTemplate) {
    return StrSubstitutor.replaceHeaderKeysInTemplate(topicNameTemplate, "%");
  }

  @Override
  public void start(WorkerDto workerDto) {
    String topic = topic(workerDto);

    logger.info(
        "start(): Posting the following message to topic " + topic + " WorkerDto.type is {}",
        workerDto.workerType);

    ContextContainer contextContainer = ContextContainer.getInstance();
    Map<String, Object> props = new HashMap<>(contextContainer.toMap());
    try {
      String s = objectMapper.writeValueAsString(workerDto);
      chenilePub.asyncPublish(topic, s, props);
    } catch (Exception e) {
      throw new ServerException("ERROR_SEND", "Error publishing worker starter message", e);
    }
  }
}
