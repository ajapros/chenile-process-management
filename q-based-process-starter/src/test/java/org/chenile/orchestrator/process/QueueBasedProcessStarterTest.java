package org.chenile.orchestrator.process;

import org.chenile.core.context.HeaderUtils;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.WorkerType;
import org.chenile.pubsub.ChenilePub;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies that {@link QueueBasedProcessStarter} publishes the worker to the topic named in the
 * process' execDef and that client isolation is preserved by forwarding the client id as the
 * tenant header only when it is present.
 */
public class QueueBasedProcessStarterTest {

	@Test
	public void publishesToConfiguredTopicWithTenantHeader() {
		ChenilePub chenilePub = mock(ChenilePub.class);
		QueueBasedProcessStarter starter = new QueueBasedProcessStarter();
		starter.chenilePub = chenilePub;

		starter.start(workerDto("feed-topic", "tenant-A"));

		@SuppressWarnings("rawtypes")
		ArgumentCaptor<Map> propsCaptor = ArgumentCaptor.forClass(Map.class);
		verify(chenilePub).asyncPublish(eq("feed-topic"), anyString(), propsCaptor.capture());
		Assert.assertEquals("tenant-A", propsCaptor.getValue().get(HeaderUtils.TENANT_ID_KEY));
	}

	@Test
	public void omitsTenantHeaderWhenClientIdIsNull() {
		ChenilePub chenilePub = mock(ChenilePub.class);
		QueueBasedProcessStarter starter = new QueueBasedProcessStarter();
		starter.chenilePub = chenilePub;

		starter.start(workerDto("feed-topic", null));

		@SuppressWarnings("rawtypes")
		ArgumentCaptor<Map> propsCaptor = ArgumentCaptor.forClass(Map.class);
		verify(chenilePub).asyncPublish(eq("feed-topic"), anyString(), propsCaptor.capture());
		Assert.assertFalse(propsCaptor.getValue().containsKey(HeaderUtils.TENANT_ID_KEY));
	}

	private WorkerDto workerDto(String queue, String clientId) {
		Process process = new Process();
		process.id = "p1";
		process.processType = "feed";
		process.clientId = clientId;
		WorkerDto workerDto = new WorkerDto();
		workerDto.process = process;
		workerDto.workerType = WorkerType.SPLITTER;
		Map<String, String> execDef = new HashMap<>();
		execDef.put("queue", queue);
		workerDto.execDef = execDef;
		return workerDto;
	}
}
