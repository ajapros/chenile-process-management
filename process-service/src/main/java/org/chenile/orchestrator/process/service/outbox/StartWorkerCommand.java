package org.chenile.orchestrator.process.service.outbox;

import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.payload.DoneWithErrorsPayload;
import org.chenile.orchestrator.process.outbox.OutboxCommand;

public class StartWorkerCommand extends AbstractProcessOutboxCommand {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(StartWorkerCommand.class);
    public static final String TYPE = "START_WORKER";
    public StartWorkerCommand(ProcessCommandSupport support) { super(support); }
    @Override public String type() { return TYPE; }

    @Override public void enqueue(ProcessTransition transition) throws Exception {
        if (!support.durable() && !support.workers.hasWorkerStarter()) return;
        WorkerDto worker = support.workers.prepare(transition.process());
        if (worker == null) return;
        worker.dispatchId = transition.process().getId() + ":" + worker.workerType;
        store(transition.process(), worker.dispatchId, worker);
    }

    @Override protected void dispatchCommand(OutboxCommand entry) throws Exception {
        WorkerDto worker = read(entry, WorkerDto.class);
        if (support.receive("WORKER", entry.idempotencyKey)) support.workers.dispatch(worker);
    }

    @Override protected void deadCommand(OutboxCommand entry) throws Exception {
        WorkerDto worker = read(entry, WorkerDto.class);
        String event = switch (worker.workerType) {
            case SPLITTER -> Constants.Events.SPLIT_DONE_WITH_ERRORS;
            case EXECUTOR -> Constants.Events.DONE_WITH_ERRORS;
            case AGGREGATOR -> Constants.Events.AGGREGATION_DONE_WITH_ERRORS;
        };
        support.independent.executeWithoutResult(tx -> {
            DoneWithErrorsPayload payload = new DoneWithErrorsPayload();
            payload.exceptionMessage = "Worker dispatch permanently failed (outbox DEAD): " + entry.idempotencyKey;
            support.managers.getObject().processById(entry.processId, event, payload);
        });
        LOG.warn("Compensated dead worker command {} by driving process {} via {}", entry.id, entry.processId, event);
    }
}
