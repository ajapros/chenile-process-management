package org.chenile.orchestrator.process;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;
import org.chenile.core.context.ContextContainer;
import org.chenile.core.context.HeaderUtils;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.WorkerType;
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
    starter.start(workerDto("feed-topic"));

    @SuppressWarnings("rawtypes")
    ArgumentCaptor<Map> propsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(chenilePub).asyncPublish(eq("feed-topic"), anyString(), propsCaptor.capture());
    Assert.assertEquals("tenant-A", propsCaptor.getValue().get(HeaderUtils.TENANT_ID_KEY));
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

  private WorkerDto workerDto(String queue) {
    Process process = new Process();
    process.id = "p1";
    process.processType = "feed";
    WorkerDto workerDto = new WorkerDto();
    workerDto.process = process;
    workerDto.workerType = WorkerType.SPLITTER;
    Map<String, String> execDef = new HashMap<>();
    execDef.put("queue", queue);
    workerDto.execDef = execDef;
    return workerDto;
  }
}
