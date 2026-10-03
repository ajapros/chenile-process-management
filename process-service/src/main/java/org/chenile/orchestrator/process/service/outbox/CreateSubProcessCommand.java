package org.chenile.orchestrator.process.service.outbox;

import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.outbox.OutboxCommand;
import java.util.UUID;

public class CreateSubProcessCommand extends AbstractProcessOutboxCommand {
    public static final String TYPE = "CREATE_SUBPROCESS";
    public CreateSubProcessCommand(ProcessCommandSupport support) { super(support); }
    @Override public String type() { return TYPE; }

    @Override public void enqueue(ProcessTransition transition) throws Exception {
        Process parent = transition.process();
        if (!Constants.States.SUB_PROCESSES_PENDING.equals(transition.state().getStateId()) || parent.subProcesses == null) return;
        for (Process child : parent.subProcesses) {
            if (child.getId() == null) child.setId(UUID.randomUUID().toString());
            child.tenant = parent.tenant;
            store(parent, "create:" + child.getId(), child);
        }
    }

    @Override protected void dispatchCommand(OutboxCommand entry) throws Exception {
        Process child = read(entry, Process.class);
        if (!support.receive("CREATE", entry.idempotencyKey)) return;
        if (!support.durable() || !support.processes.existsById(child.getId())) support.managers.getObject().create(child);
    }
}
