package org.chenile.orchestrator.process.service.outbox;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.outbox.OutboxCommandRegistry;
import org.chenile.stm.State;

/** Entry-action adapter. All planning and execution behavior belongs to registered commands. */
public class ProcessEffects {
    private final OutboxCommandRegistry<ProcessTransition> commands;
    public ProcessEffects(OutboxCommandRegistry<ProcessTransition> commands) { this.commands = commands; }

    public void enqueue(Process process, State state) throws Exception {
        commands.enqueue(new ProcessTransition(process, state));
    }
}
