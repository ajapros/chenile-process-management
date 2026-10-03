package org.chenile.orchestrator.process.service.outbox;

import org.chenile.core.context.ContextContainer;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.outbox.OutboxCommand;
import org.chenile.orchestrator.process.outbox.OutboxCommandLifecycle;

/** Shared tenant isolation and JSON persistence, not a central command switch. */
public abstract class AbstractProcessOutboxCommand implements OutboxCommandLifecycle<ProcessTransition> {
    protected final ProcessCommandSupport support;
    protected AbstractProcessOutboxCommand(ProcessCommandSupport support) { this.support = support; }

    protected void store(Process process, String key, Object data) throws Exception {
        support.enqueue(this, process, key, data);
    }

    protected <T> T read(OutboxCommand entry, Class<T> type) throws Exception {
        return support.read(entry, type);
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
            support.flushTenantScopedChanges();
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
