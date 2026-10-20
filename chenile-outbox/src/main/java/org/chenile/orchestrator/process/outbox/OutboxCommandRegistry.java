package org.chenile.orchestrator.process.outbox;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Dispatch registration is independent of producer context types. */
public class OutboxCommandRegistry {
  private final Map<String, OutboxCommandLifecycle<?>> commands = new LinkedHashMap<>();

  public OutboxCommandRegistry(Collection<? extends OutboxCommandLifecycle<?>> implementations) {
    for (var command : implementations) {
      if (command.type() == null || command.type().isBlank())
        throw new IllegalArgumentException("Outbox command type is required");
      if (commands.putIfAbsent(command.type(), command) != null)
        throw new IllegalArgumentException("Duplicate outbox command type: " + command.type());
    }
  }

  public OutboxCommandLifecycle<?> resolve(String type) {
    var command = commands.get(type);
    if (command == null)
      throw new IllegalArgumentException("Unregistered outbox command type: " + type);
    return command;
  }
}
