package org.chenile.orchestrator.process.service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.process.api.ProcessManager;
import org.chenile.orchestrator.process.outbox.ProcessOutboxRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.outbox.OutboxCommand;
import java.util.UUID;

import org.chenile.orchestrator.process.configuration.dao.ProcessRepository;

public class CreateSubProcessCommand extends AbstractProcessOutboxCommand {
    public static final String TYPE = "CREATE_SUBPROCESS";
    private final ProcessRepository processes;
    public CreateSubProcessCommand(ProcessOutboxRepository outbox, ProcessManager processManager,
            ObjectMapper mapper, PlatformTransactionManager transactionManager, ProcessRepository processes) {
        super(outbox, managers, mapper, transactionManager);
        this.processes = processes;
    }
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
        if (!checkIdempotency("CREATE", entry.idempotencyKey)) return;
        if (!processes.existsById(child.getId())) managers.getObject().create(child);
    }
}
