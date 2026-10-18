package org.chenile.orchestrator.process.service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.process.outbox.ProcessOutboxRepository;
import org.springframework.transaction.PlatformTransactionManager;

import org.chenile.core.context.HeaderUtils;
import org.chenile.core.event.EventProcessor;
import java.util.HashMap;
import org.chenile.orchestrator.process.model.ProcessCompletedEvent;
import org.chenile.orchestrator.process.outbox.OutboxCommand;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.service.CompletionArguments;
import org.chenile.stm.State;

public class EmitCompletedCommand extends AbstractProcessOutboxCommand {
    private final EventProcessor events;
    public static final String TYPE = "EMIT_COMPLETED";
    public EmitCompletedCommand(ProcessOutboxRepository outbox,
            ObjectMapper mapper, PlatformTransactionManager transactionManager,
            EventProcessor events) {
        super(outbox, mapper, transactionManager);
        this.events = events;
    }
    @Override public String type() { return TYPE; }

    @Override public boolean shouldEnqueue(ProcessTransition transition) { return terminal(transition); }

    @Override public void enqueue(ProcessTransition transition) throws Exception {
        var event = completedEvent(transition.process(), transition.state());

        store(transition.process(), transition.process().getId() + ":completed", event);
    }

    @Override protected void dispatchCommand(OutboxCommand entry) throws Exception {
        ProcessCompletedEvent event = read(entry, ProcessCompletedEvent.class);
        var headers = new HashMap<String, String>();
        if (event.tenantId != null) headers.put(HeaderUtils.TENANT_ID_KEY, event.tenantId);
        if (event.triggerId != null) headers.put("x-chenile-trigger-id", event.triggerId);
        var exchanges = events.handleEvent(Constants.Events.PROCESS_COMPLETED, event, headers);
        if (exchanges.isEmpty()) throw new IllegalStateException("No ProcessCompleted subscriber configured");
        for (var exchange : exchanges) {
            if (exchange.getException() != null)
                throw new IllegalStateException("Completion subscriber failed for " + event.processId, exchange.getException());
        }
    }
    public static ProcessCompletedEvent completedEvent(Process process, State terminalState) {
        ProcessCompletedEvent event = new ProcessCompletedEvent();
        event.generatedAt = (process.getStateEntryTime() == null ? java.time.Instant.now()
                : process.getStateEntryTime().toInstant()).toString();
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
