package org.chenile.orchestrator.process;

import org.chenile.core.event.EventProcessor;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.WorkerType;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies that {@link InVMProcessStarter} serializes the WorkerDto and hands it to the in-VM
 * {@link EventProcessor} so that the work is dispatched inside the same JVM.
 */
public class InVMProcessStarterTest {

	@Test
	public void forwardsSerializedWorkerDtoToEventProcessor() {
		EventProcessor eventProcessor = mock(EventProcessor.class);
		InVMProcessStarter starter = new InVMProcessStarter();
		starter.eventProcessor = eventProcessor;

		Process process = new Process();
		process.id = "p1";
		process.processType = "feed";
		WorkerDto workerDto = new WorkerDto();
		workerDto.process = process;
		workerDto.workerType = WorkerType.AGGREGATOR;

		starter.start(workerDto);

		ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
		verify(eventProcessor).handleEvent(eq("topic1"), messageCaptor.capture());
		Assert.assertTrue("serialized payload should carry the worker type",
				messageCaptor.getValue().contains("AGGREGATOR"));
	}
}
