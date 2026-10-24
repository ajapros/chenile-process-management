package org.chenile.orchestrator.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.chenile.base.exception.ConfigurationException;
import org.chenile.base.exception.ServerException;
import org.chenile.core.context.ContextContainer;
import org.chenile.core.context.HeaderUtils;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.workers.WorkListenerInput;
import org.chenile.orchestrator.process.workers.WorkerConstants;
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

  /**
   * Use this as an extension point in a sub class if additional headers need to be set.
   * @param workerDto - the worker dto
   * @return the headers.
   */
  protected Map<String,Object> headers(WorkerDto workerDto) {
    ContextContainer contextContainer = ContextContainer.getInstance();
    Map<String, Object> props = new HashMap<>();
    contextContainer
            .toMap()
            .forEach(
                    (key, value) -> {
                      if (key.startsWith("x-")) {
                        props.put(key, value);
                      }
                    });
    if (workerDto.process.tenant != null) {
      props.put(HeaderUtils.TENANT_ID_KEY, workerDto.process.tenant);
    }
    return props;
  }

  @Override
  public void start(WorkerDto workerDto) {
    String topic = topic(workerDto);

    logger.info(
        "start(): Posting process work to topic {}. Worker type is {}",
        topic,
        workerDto.workerType);

    Map<String,Object> props = headers(workerDto);
    props.put(WorkerConstants.PROCESS_TYPE, workerDto.process.processType);
    props.put(WorkerConstants.WORKER_TYPE, workerDto.workerType);
    try {
      WorkListenerInput<Object> workListenerInput = makeWorkListenerInput(workerDto);
      String s = objectMapper.writeValueAsString(workListenerInput);
      chenilePub.asyncPublish(topic, s, props);
    } catch (Exception e) {
      throw new ServerException("ERROR_SEND", "Error publishing worker starter message", e);
    }
  }

  public void start1(WorkerDto workerDto) {
    String topic = topic(workerDto);
    Map<String,Object> props = headers(workerDto);
    try {
      String s = objectMapper.writeValueAsString(workerDto);
      chenilePub.asyncPublish(topic, s, props);
    } catch (Exception e) {
      throw new ServerException("ERROR_SEND", "Error publishing worker starter message", e);
    }
  }

  private WorkListenerInput<Object> makeWorkListenerInput(WorkerDto workerDto) throws Exception {
    WorkListenerInput<Object> workListenerInput = new WorkListenerInput<>();
    workListenerInput.processId = workerDto.process.id;
    workListenerInput.dispatchId = workerDto.dispatchId;
    workListenerInput.execDef = workerDto.execDef;
    if (workerDto.process.input != null) {
      workListenerInput.payload = objectMapper.readTree(workerDto.process.input);
    }
    return workListenerInput;
  }
}
