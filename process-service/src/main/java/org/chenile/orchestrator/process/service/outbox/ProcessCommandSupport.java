package org.chenile.orchestrator.process.service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.core.context.HeaderUtils;
import org.chenile.core.event.EventProcessor;
import org.chenile.orchestrator.process.api.ProcessManager;
import org.chenile.orchestrator.process.configuration.dao.ProcessRepository;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.ProcessCompletedEvent;
import org.chenile.orchestrator.process.outbox.ProcessOutboxRepository;
import org.chenile.orchestrator.process.outbox.OutboxCommand;
import org.chenile.orchestrator.process.outbox.OutboxCommandLifecycle;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.service.defs.PostSaveHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.HashMap;

/** Shared dependencies/transaction helpers; no command selection or command-specific behavior. */
public class ProcessCommandSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessCommandSupport.class);
    final ProcessOutboxRepository outbox;
    final ObjectProvider<ProcessManager> managers;
    final ProcessRepository processes;
    final PostSaveHook workers;
    final ObjectMapper mapper = new ObjectMapper();
    final TransactionTemplate independent;
    private final EventProcessor events;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public ProcessCommandSupport(ProcessOutboxRepository outbox, ObjectProvider<ProcessManager> managers,
            ProcessRepository processes, PostSaveHook workers,
            EventProcessor events, PlatformTransactionManager manager) {
        this.outbox = outbox; this.managers = managers; this.processes = processes;
        this.workers = workers; this.events = events;
        independent = new TransactionTemplate(manager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean durable() { return outbox != null; }

    /** JPA callbacks derive tenant from thread context; flush before a command restores that context. */
    void flushTenantScopedChanges() {
        if (entityManager != null && TransactionSynchronizationManager.isActualTransactionActive())
            entityManager.flush();
    }

    public void enqueue(OutboxCommandLifecycle<?> command, Process process, String key, Object body) throws Exception {
        OutboxCommand entry = OutboxCommand.create(process.getId(), command.type(), key,
                durable() ? mapper.writeValueAsString(body) : null);
        entry.tenantId = process.tenant;
        if (durable()) outbox.enqueue(entry);
        else {
            entry.attachInlinePayload(body);
            command.dispatch(entry);
        }
    }

    public boolean receive(String consumer, String key) {
        return !durable() || outbox.receive(consumer, key);
    }

    public <T> T read(OutboxCommand entry, Class<T> type) throws Exception {
        T local = entry.inlinePayload(type);
        return local == null ? mapper.readValue(entry.payload, type) : local;
    }

    void publishInline(ProcessCompletedEvent event) {
        var headers = new HashMap<String, String>();
        if (event.tenantId != null) headers.put(HeaderUtils.TENANT_ID_KEY, event.tenantId);
        if (event.triggerId != null) headers.put("triggerId", event.triggerId);
        for (var exchange : events.handleEvent(Constants.Events.PROCESS_COMPLETED, event, headers))
            if (exchange.getException() != null)
                LOG.warn("Completion subscriber failed for {}", event.processId, exchange.getException());
    }

    void publishAfterCommit(ProcessCompletedEvent event) {
        // afterCommit still has the old resources bound: never join the completed transaction.
        try {
            independent.executeWithoutResult(tx -> publishInline(event));
        } catch (Exception failure) { LOG.warn("Best-effort completion publication failed for {}", event.processId, failure); }
    }
}
