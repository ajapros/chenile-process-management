package org.chenile.orchestrator.process.utils.base;

import org.chenile.orchestrator.delegate.ProcessManagerClient;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.WorkerType;
import org.chenile.orchestrator.process.model.payload.DoneWithErrorsPayload;
import org.chenile.orchestrator.process.utils.api.IWorker;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchServiceBaseTest {
    @Test
    void acceptsAdditiveFieldsInWorkerPayloads() {
        ProcessManagerClient client = mock(ProcessManagerClient.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        PayloadWorker worker = new PayloadWorker();
        when(applicationContext.getBean("feedSplitter")).thenReturn(worker);
        TestBatchService service = new TestBatchService(client, applicationContext);

        boolean started = service.startWorker(workerDto(WorkerType.SPLITTER));

        assertTrue(started);
        assertTrue(worker.started);
        assertEquals("value", worker.payload.known);
        verify(client, never()).process(any(), any(), any());
    }

    @Test
    void reportsMalformedSplitterInputToTheDaemon() {
        ProcessManagerClient client = mock(ProcessManagerClient.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        PayloadWorker worker = new PayloadWorker();
        when(applicationContext.getBean("feedSplitter")).thenReturn(worker);

        boolean started = new TestBatchService(client, applicationContext)
                .startWorker(workerDto(WorkerType.SPLITTER, "{invalid}"));

        assertTrue(started);
        assertFalse(worker.started);
        ArgumentCaptor<DoneWithErrorsPayload> payload = ArgumentCaptor.forClass(DoneWithErrorsPayload.class);
        verify(client).process(eq("process-1"), eq("splitDoneWithErrors"), payload.capture());
        assertTrue(payload.getValue().exceptionMessage.contains("Unable to invoke worker"));
        assertTrue(payload.getValue().stackTrace.contains("JsonParseException"));
    }

    @Test
    void reportsFailuresUsingTheMatchingWorkerErrorEvent() {
        assertReportedEvent(WorkerType.AGGREGATOR, "aggregationDoneWithErrors");
        assertReportedEvent(WorkerType.EXECUTOR, "doneWithErrors");
    }

    @Test
    void reportsWorkersWhosePayloadTypeCannotBeDerived() {
        ProcessManagerClient client = mock(ProcessManagerClient.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean("feedSplitter")).thenReturn(new UntypedWorker());

        boolean started = new TestBatchService(client, applicationContext)
                .startWorker(workerDto(WorkerType.SPLITTER, "{invalid}"));

        assertTrue(started);
        ArgumentCaptor<DoneWithErrorsPayload> payload = ArgumentCaptor.forClass(DoneWithErrorsPayload.class);
        verify(client).process(eq("process-1"), eq("splitDoneWithErrors"), payload.capture());
        assertTrue(payload.getValue().exceptionMessage.contains("Cannot derive type information"));
    }

    @Test
    void leavesTheWorkItemRetryableWhenFailureReportingCannotReachTheDaemon() {
        ProcessManagerClient client = mock(ProcessManagerClient.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean("feedSplitter")).thenReturn(new PayloadWorker());
        when(client.process(any(), any(), any())).thenThrow(new IllegalStateException("daemon unavailable"));

        boolean started = new TestBatchService(client, applicationContext)
                .startWorker(workerDto(WorkerType.SPLITTER, "{invalid}"));

        assertFalse(started);
    }

    private void assertReportedEvent(WorkerType workerType, String eventId) {
        ProcessManagerClient client = mock(ProcessManagerClient.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean("feed" + camelCase(workerType))).thenReturn(new PayloadWorker());

        boolean started = new TestBatchService(client, applicationContext)
                .startWorker(workerDto(workerType, "{\"known\":{\"nested\":true}}"));

        assertTrue(started);
        verify(client).process(eq("process-1"), eq(eventId), any(DoneWithErrorsPayload.class));
    }

    private WorkerDto workerDto(WorkerType workerType) {
        return workerDto(workerType, "{\"known\":\"value\",\"unknown\":true}");
    }

    private WorkerDto workerDto(WorkerType workerType, String input) {
        Process process = new Process();
        process.id = "process-1";
        process.processType = "feed";
        process.input = input;
        WorkerDto workerDto = new WorkerDto();
        workerDto.process = process;
        workerDto.workerType = workerType;
        return workerDto;
    }

    private String camelCase(WorkerType workerType) {
        String value = workerType.name().toLowerCase();
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    private static class TestBatchService extends BatchServiceBase<Object> {
        TestBatchService(ProcessManagerClient client, ApplicationContext context) {
            processManagerClient = client;
            applicationContext = context;
        }

        @Override
        protected String getClientName() {
            return "test";
        }
    }

    private static class PayloadWorker implements IWorker<Payload> {
        private boolean started;
        private Payload payload;

        public void doStart(WorkerDto workerDto, Payload payload) {
        }

        @Override
        public void start(WorkerDto workerDto, Payload payload) {
            started = true;
            this.payload = payload;
        }
    }

    @SuppressWarnings("rawtypes")
    private static class UntypedWorker implements IWorker {
        @Override
        public void start(WorkerDto workerDto, Object payload) {
        }
    }

    private static class Payload {
        public String known;
    }
}
