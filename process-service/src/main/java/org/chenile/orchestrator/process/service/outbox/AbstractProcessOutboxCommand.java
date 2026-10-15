package org.chenile.orchestrator.process.service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.chenile.core.context.ContextContainer;
import org.chenile.orchestrator.process.api.ProcessManager;
import org.chenile.orchestrator.process.outbox.ProcessOutboxRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.outbox.OutboxCommand;
import org.chenile.orchestrator.process.outbox.OutboxCommandLifecycle;

/** Shared tenant isolation and JSON persistence, not a central command switch. */
public abstract class AbstractProcessOutboxCommand implements OutboxCommandLifecycle<ProcessTransition> {
    private final ProcessOutboxRepository outbox;
    protected final ObjectProvider<ProcessManager> managers;
    protected final ObjectMapper mapper;
    protected final TransactionTemplate independent;
    @PersistenceContext
    private EntityManager entityManager;

    protected AbstractProcessOutboxCommand(ProcessOutboxRepository outbox, ObjectProvider<ProcessManager> managers,
            ObjectMapper mapper, PlatformTransactionManager transactionManager) {
        this.outbox = outbox;
        this.managers = managers;
        this.mapper = mapper;
        independent = new TransactionTemplate(transactionManager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    protected final boolean durable() { return outbox != null; }

    protected void store(Process process, String key, Object data) throws Exception {
        OutboxCommand entry = OutboxCommand.create(process.getId(), type(), key,
                durable() ? mapper.writeValueAsString(data) : null);
        entry.tenantId = process.tenant;
        if (durable()) outbox.enqueue(entry);
        else {
            entry.attachInlinePayload(data);
            dispatch(entry);
        }
    }

    protected <T> T read(OutboxCommand entry, Class<T> type) throws Exception {
        T local = entry.inlinePayload(type);
        return local == null ? mapper.readValue(entry.payload, type) : local;
    }

    /** True permits execution; durable mode also records the claim in the current transaction. */
    protected final boolean checkIdempotency(String consumer, String key) {
        return !durable() || outbox.checkIdempotency(consumer, key);
    }

    /** Flush tenant-derived JPA callbacks before returning to the caller's context. */
    private void flushTenantScopedChanges() {
        if (entityManager != null && TransactionSynchronizationManager.isActualTransactionActive())
            entityManager.flush();
    }

    protected boolean terminal(ProcessTransition transition) {
        String state = transition.state().getStateId();
        return Constants.States.PROCESSED.equals(state) || Constants.States.PROCESSED_WITH_ERRORS.equals(state);
    }

    @Override public final void dispatch(OutboxCommand entry) throws Exception {
        var context = ContextContainer.getInstance();
        var snapshot = context.snapshot();
        try {
            context.setTenant(entry.tenantId);
            dispatchCommand(entry);
            flushTenantScopedChanges();
        }
        finally { context.restore(snapshot); }
    }

    @Override public final void onDead(OutboxCommand entry) throws Exception {
        var context = ContextContainer.getInstance();
        var snapshot = context.snapshot();
        try { context.setTenant(entry.tenantId); deadCommand(entry); }
        finally { context.restore(snapshot); }
    }

    protected abstract void dispatchCommand(OutboxCommand entry) throws Exception;
    protected void deadCommand(OutboxCommand entry) throws Exception { } // operator replay by default
}
