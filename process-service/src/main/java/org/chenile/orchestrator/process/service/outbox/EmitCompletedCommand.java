package org.chenile.orchestrator.process.service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.process.api.ProcessManager;
import org.chenile.orchestrator.process.outbox.ProcessOutboxRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

import org.chenile.core.context.HeaderUtils;
import org.chenile.core.event.EventProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import org.chenile.orchestrator.process.model.ProcessCompletedEvent;
import org.chenile.orchestrator.process.outbox.OutboxCommand;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.service.CompletionArguments;
import org.chenile.orchestrator.process.configuration.dao.CompletionEventRepository;
import org.chenile.orchestrator.process.configuration.model.CompletionEventRecord;
import org.chenile.stm.State;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class EmitCompletedCommand extends AbstractProcessOutboxCommand {
    private static final Logger LOG = LoggerFactory.getLogger(EmitCompletedCommand.class);
    private final CompletionEventRepository history;
    private final EventProcessor events;
    public static final String TYPE = "EMIT_COMPLETED";
    public EmitCompletedCommand(ProcessOutboxRepository outbox, ObjectProvider<ProcessManager> managers,
            ObjectMapper mapper, PlatformTransactionManager transactionManager,
            CompletionEventRepository history, EventProcessor events) {
        super(outbox, managers, mapper, transactionManager);
        this.history = history;
        this.events = events;
    }
    @Override public String type() { return TYPE; }

    @Override public void enqueue(ProcessTransition transition) throws Exception {
        if (!terminal(transition)) return;
        var event = completedEvent(transition.process(), transition.state());
        if (history != null) {
            var existing = history.findById(event.processId);
            if (existing.isPresent()) {
                event = mapper.readValue(existing.get().payload, ProcessCompletedEvent.class);
                store(transition.process(), transition.process().getId() + ":completed", event);
                return;
            }
            var record = new CompletionEventRecord();
            record.processId = event.processId;
            record.tenant = event.tenantId;
            record.triggerId = event.triggerId;
            record.generatedAt = event.generatedAt;
            record.payload = mapper.writeValueAsString(event);
            history.saveAndFlush(record);
        }
        store(transition.process(), transition.process().getId() + ":completed", event);
    }

    @Override protected void dispatchCommand(OutboxCommand entry) throws Exception {
        ProcessCompletedEvent event = read(entry, ProcessCompletedEvent.class);
        if (!durable()) { publishInline(event); return; }
        managers.getObject().processCompleted(event);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { publishAfterCommit(event); }
        });
    }
    public static ProcessCompletedEvent completedEvent(Process process, State terminalState) {
        ProcessCompletedEvent event = new ProcessCompletedEvent();
        event.generatedAt = java.time.Instant.now().toString();
        event.processId = process.getId();
        event.processType = process.processType;
        event.processDefName = process.processType;
        event.description = process.description;
        event.parentId = process.parentId;
        event.triggerId = process.triggerId;
        event.tenantId = process.tenant;
        event.state = terminalState == null ? null : terminalState.getStateId();
        event.input = process.input;
        event.output = process.output;
        event.args = CompletionArguments.from(process.input, process.output);
        event.successful = Constants.States.PROCESSED.equals(event.state);
        return event;
    }
    private void publishInline(ProcessCompletedEvent event) {
        var headers = new HashMap<String, String>();
        if (event.tenantId != null) headers.put(HeaderUtils.TENANT_ID_KEY, event.tenantId);
        if (event.triggerId != null) headers.put("triggerId", event.triggerId);
        for (var exchange : events.handleEvent(Constants.Events.PROCESS_COMPLETED, event, headers))
            if (exchange.getException() != null)
                LOG.warn("Completion subscriber failed for {}", event.processId, exchange.getException());
    }

    private void publishAfterCommit(ProcessCompletedEvent event) {
        // afterCommit still has the old resources bound: never join the completed transaction.
        try {
            independent.executeWithoutResult(tx -> publishInline(event));
        } catch (Exception failure) { LOG.warn("Best-effort completion publication failed for {}", event.processId, failure); }
    }
}
