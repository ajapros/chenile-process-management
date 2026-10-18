package org.chenile.orchestrator.process.service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.process.api.ProcessManager;
import org.chenile.orchestrator.process.outbox.ProcessOutboxRepository;
import org.springframework.transaction.PlatformTransactionManager;

import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.config.reader.IProcessConfigurator;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.WorkerType;
import org.chenile.orchestrator.process.model.payload.DoneWithErrorsPayload;
import org.chenile.orchestrator.process.outbox.OutboxCommand;
import org.springframework.beans.factory.ObjectProvider;
import java.util.HashMap;

public class StartWorkerCommand extends AbstractProcessOutboxCommand {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(StartWorkerCommand.class);
    private final ObjectProvider<ProcessManager> managers;
    public static final String TYPE = "START_WORKER";
    private final IProcessConfigurator configurator;
    private final ObjectProvider<WorkerStarter> starters;

    public StartWorkerCommand(ProcessOutboxRepository outbox, ObjectProvider<ProcessManager> managers,
            ObjectMapper mapper, PlatformTransactionManager transactionManager, IProcessConfigurator configurator,
            ObjectProvider<WorkerStarter> starters) {
        super(outbox, mapper, transactionManager);
        this.managers = managers;
        this.configurator = configurator;
        this.starters = starters;
    }
    @Override public String type() { return TYPE; }

    @Override public boolean shouldEnqueue(ProcessTransition transition) {
        var process = transition.process();
        return !process.skipPostWorkerCreation && workerType(process) != null
                && configurator.findByName(process.processType) != null;
    }

    @Override public void enqueue(ProcessTransition transition) throws Exception {
        WorkerDto worker = prepare(transition.process());
        worker.dispatchId = transition.process().getId() + ":" + worker.workerType;
        store(transition.process(), worker.dispatchId, worker);
    }

    @Override protected void dispatchCommand(OutboxCommand entry) throws Exception {
        WorkerDto worker = read(entry, WorkerDto.class);
        if (checkIdempotency("WORKER", entry.idempotencyKey)) {
            WorkerStarter starter = starters.getIfAvailable();
            if (starter == null) throw new IllegalStateException("No WorkerStarter configured for durable dispatch");
            starter.start(worker);
        }
    }

    /** Snapshot the work definition before durable delivery. */
    private WorkerDto prepare(Process process) {
        var definition = configurator.findByName(process.processType);
        WorkerType type = workerType(process);
        LOG.info("State = {}. Starting worker type = {}", process.getCurrentState().getStateId(), type);
        WorkerDto worker = new WorkerDto();
        worker.process = process;
        worker.workerType = type;
        worker.execDef = new HashMap<>();
        if (definition.config != null) worker.execDef.putAll(definition.config);
        return worker;
    }

    private WorkerType workerType(Process process) {
        return switch (process.getCurrentState().getStateId()) {
            case Constants.States.SPLIT_PENDING -> WorkerType.SPLITTER;
            case Constants.States.EXECUTING -> WorkerType.EXECUTOR;
            case Constants.States.AGGREGATION_PENDING -> WorkerType.AGGREGATOR;
            default -> null;
        };
    }

    @Override protected void deadCommand(OutboxCommand entry) throws Exception {
        WorkerDto worker = read(entry, WorkerDto.class);
        String event = switch (worker.workerType) {
            case SPLITTER -> Constants.Events.SPLIT_DONE_WITH_ERRORS;
            case EXECUTOR -> Constants.Events.DONE_WITH_ERRORS;
            case AGGREGATOR -> Constants.Events.AGGREGATION_DONE_WITH_ERRORS;
        };
        independent.executeWithoutResult(tx -> {
            DoneWithErrorsPayload payload = new DoneWithErrorsPayload();
            payload.exceptionMessage = "Worker dispatch permanently failed (outbox DEAD): " + entry.idempotencyKey;
            managers.getObject().processById(entry.processId, event, payload);
        });
        LOG.warn("Compensated dead worker command {} by driving process {} via {}", entry.id, entry.processId, event);
    }
}
