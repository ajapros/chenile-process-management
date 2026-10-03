package org.chenile.orchestrator.process.outbox;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Extensible by registration, without an enum, switch, or database change. */
public class OutboxCommandRegistry<C> {
    private final Map<String, OutboxCommandLifecycle<C>> commands = new LinkedHashMap<>();

    public OutboxCommandRegistry(Collection<? extends OutboxCommandLifecycle<C>> implementations) {
        for (var command : implementations) {
            if (command.type() == null || command.type().isBlank())
                throw new IllegalArgumentException("Outbox command type is required");
            if (commands.putIfAbsent(command.type(), command) != null)
                throw new IllegalArgumentException("Duplicate outbox command type: " + command.type());
        }
    }

    public void enqueue(C context) throws Exception {
        for (var command : commands.values()) command.enqueue(context);
    }

    public OutboxCommandLifecycle<C> resolve(String type) {
        var command = commands.get(type);
        if (command == null) throw new IllegalArgumentException("Unregistered outbox command type: " + type);
        return command;
    }
}
