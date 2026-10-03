package org.chenile.orchestrator.process.service.outbox;

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
    private CompletionEventRepository history;
    public static final String TYPE = "EMIT_COMPLETED";
    public EmitCompletedCommand(ProcessCommandSupport support) { super(support); }
    public EmitCompletedCommand(ProcessCommandSupport support,
            CompletionEventRepository history) {
        this(support); this.history = history;
    }
    @Override public String type() { return TYPE; }

    @Override public void enqueue(ProcessTransition transition) throws Exception {
        if (!terminal(transition)) return;
        var event = completedEvent(transition.process(), transition.state());
        if (history != null) {
            var existing = history.findById(event.processId);
            if (existing.isPresent()) {
                event = support.mapper.readValue(existing.get().payload, ProcessCompletedEvent.class);
                store(transition.process(), transition.process().getId() + ":completed", event);
                return;
            }
            var record = new CompletionEventRecord();
            record.processId = event.processId;
            record.tenant = event.tenantId;
            record.triggerId = event.triggerId;
            record.generatedAt = event.generatedAt;
            record.payload = support.mapper.writeValueAsString(event);
            history.saveAndFlush(record);
        }
        store(transition.process(), transition.process().getId() + ":completed", event);
    }

    @Override protected void dispatchCommand(OutboxCommand entry) throws Exception {
        ProcessCompletedEvent event = read(entry, ProcessCompletedEvent.class);
        if (!support.durable()) { support.publishInline(event); return; }
        support.managers.getObject().processCompleted(event);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { support.publishAfterCommit(event); }
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
}
