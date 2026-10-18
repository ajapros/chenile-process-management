package org.chenile.orchestrator.process.outbox;

import java.util.Collection;
import java.util.List;

/** A producer only selects commands for its own context type; eligibility performs no writes. */
public class OutboxCommandProducer<C> {
    private final List<? extends OutboxCommandLifecycle<C>> commands;
    public OutboxCommandProducer(Collection<? extends OutboxCommandLifecycle<C>> commands) {
        this.commands = List.copyOf(commands);
    }
    public void enqueue(C context) throws Exception {
        for (var command : commands)
            if (command.shouldEnqueue(context)) command.enqueue(context);
    }
}
