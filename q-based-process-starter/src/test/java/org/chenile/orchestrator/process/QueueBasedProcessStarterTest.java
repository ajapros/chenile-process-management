package org.chenile.orchestrator.process;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.chenile.core.context.ContextContainer;
import org.chenile.core.context.HeaderUtils;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.WorkerType;
import org.chenile.orchestrator.process.workers.WorkerConstants;
import org.chenile.pubsub.ChenilePub;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Verifies that {@link QueueBasedProcessStarter} publishes the worker to the topic named in the
 * process' execDef and forwards the tenant header from the calling context when present.
 */
public class QueueBasedProcessStarterTest {
  private final ContextContainer context = ContextContainer.getInstance();
  private ContextContainer.ContextSnapshot previousContext;

  @Before
  public void isolateContext() {
    previousContext = context.snapshot();
    context.clear();
  }

  @After
  public void restoreContext() {
    context.restore(previousContext);
  }

  @Test
  public void publishesToConfiguredTopicWithTenantHeader() {
    ChenilePub chenilePub = mock(ChenilePub.class);
    QueueBasedProcessStarter starter = new QueueBasedProcessStarter();
    starter.chenilePub = chenilePub;

    context.setTenant("tenant-A");
    context.put("authorization", "internal-value");
    context.put("x-chenile-trace", "trace-A");
    starter.start(workerDto("feed-topic"));

    @SuppressWarnings("rawtypes")
    ArgumentCaptor<Map> propsCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
    verify(chenilePub).asyncPublish(eq("feed-topic"), payloadCaptor.capture(), propsCaptor.capture());
    Assert.assertEquals("tenant-A", propsCaptor.getValue().get(HeaderUtils.TENANT_ID_KEY));
    Assert.assertEquals("trace-A", propsCaptor.getValue().get("x-chenile-trace"));
    Assert.assertFalse(propsCaptor.getValue().containsKey("authorization"));
    Assert.assertEquals("feed", propsCaptor.getValue().get(WorkerConstants.PROCESS_TYPE));
    Assert.assertEquals(WorkerType.SPLITTER, propsCaptor.getValue().get(WorkerConstants.WORKER_TYPE));
    try {
      JsonNode message = new ObjectMapper().readTree(payloadCaptor.getValue());
      assertEquals("p1", message.get("processId").asText());
      assertEquals("input", message.at("/payload/value").asText());
      assertEquals("dispatch-1", message.get("dispatchId").asText());
      assertEquals("feed-topic", message.at("/execDef/queue").asText());
    } catch (Exception exception) {
      throw new AssertionError("Queue message must contain a typed work input", exception);
    }
  }

  @Test
  public void omitsTenantHeaderWhenAbsentFromContext() {
    ChenilePub chenilePub = mock(ChenilePub.class);
    QueueBasedProcessStarter starter = new QueueBasedProcessStarter();
    starter.chenilePub = chenilePub;

    starter.start(workerDto("feed-topic"));

    @SuppressWarnings("rawtypes")
    ArgumentCaptor<Map> propsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(chenilePub).asyncPublish(eq("feed-topic"), anyString(), propsCaptor.capture());
    Assert.assertFalse(propsCaptor.getValue().containsKey(HeaderUtils.TENANT_ID_KEY));
  }

  @Test
  public void publishesTheLegacyWorkerDtoOnlyThroughStart1() {
    ChenilePub chenilePub = mock(ChenilePub.class);
    QueueBasedProcessStarter starter = new QueueBasedProcessStarter();
    starter.chenilePub = chenilePub;

    starter.start1(workerDto("feed-topic"));

    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
    verify(chenilePub).asyncPublish(eq("feed-topic"), payloadCaptor.capture(), any(Map.class));
    try {
      JsonNode message = new ObjectMapper().readTree(payloadCaptor.getValue());
      Assert.assertTrue(message.has("process"));
      Assert.assertTrue(message.has("workerType"));
      Assert.assertFalse(message.has("processId"));
    } catch (Exception exception) {
      throw new AssertionError("start1 must preserve the legacy WorkerDto message", exception);
    }
  }

  private WorkerDto workerDto(String queue) {
    Process process = new Process();
    process.id = "p1";
    process.processType = "feed";
    process.input = "{\"value\":\"input\"}";
    WorkerDto workerDto = new WorkerDto();
    workerDto.process = process;
    workerDto.workerType = WorkerType.SPLITTER;
    workerDto.dispatchId = "dispatch-1";
    Map<String, String> execDef = new HashMap<>();
    execDef.put("queue", queue);
    workerDto.execDef = execDef;
    return workerDto;
  }
}
