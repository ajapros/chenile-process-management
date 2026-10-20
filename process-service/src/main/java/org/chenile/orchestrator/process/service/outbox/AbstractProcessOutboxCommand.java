package org.chenile.orchestrator.process.service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.chenile.core.context.ContextContainer;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.outbox.OutboxCommand;
import org.chenile.orchestrator.process.outbox.OutboxCommandLifecycle;
import org.chenile.orchestrator.process.outbox.ProcessOutboxRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Shared tenant isolation and JSON persistence, not a central command switch. */
public abstract class AbstractProcessOutboxCommand
    implements OutboxCommandLifecycle<ProcessTransition> {
  private final ProcessOutboxRepository outbox;
  protected final ObjectMapper mapper;
  protected final TransactionTemplate independent;
  @PersistenceContext private EntityManager entityManager;

  protected AbstractProcessOutboxCommand(
      ProcessOutboxRepository outbox,
      ObjectMapper mapper,
      PlatformTransactionManager transactionManager) {
    this.outbox = java.util.Objects.requireNonNull(outbox, "Process outbox is required");
    this.mapper = mapper;
    independent = new TransactionTemplate(transactionManager);
    independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  protected void store(Process process, String key, Object data) throws Exception {
    OutboxCommand entry =
        OutboxCommand.create(process.getId(), type(), key, mapper.writeValueAsString(data));
    entry.tenantId = process.tenant;
    outbox.enqueue(entry);
  }

  protected <T> T read(OutboxCommand entry, Class<T> type) throws Exception {
    return mapper.readValue(entry.payload, type);
  }

  /** True permits execution and records the claim in the current transaction. */
  protected final boolean checkIdempotency(String consumer, String key) {
    return outbox.checkIdempotency(consumer, key);
  }

  /** Flush tenant-derived JPA callbacks before returning to the caller's context. */
  private void flushTenantScopedChanges() {
    if (entityManager != null && TransactionSynchronizationManager.isActualTransactionActive())
      entityManager.flush();
  }

  protected boolean terminal(ProcessTransition transition) {
    String state = transition.state().getStateId();
    return Constants.States.PROCESSED.equals(state)
        || Constants.States.PROCESSED_WITH_ERRORS.equals(state);
  }

  @Override
  public final void dispatch(OutboxCommand entry) throws Exception {
    inTenantContext(
        entry.tenantId,
        () -> {
          dispatchCommand(entry);
          flushTenantScopedChanges();
        });
  }

  @Override
  public final void onDead(OutboxCommand entry) throws Exception {
    inTenantContext(entry.tenantId, () -> deadCommand(entry));
  }

  /** Install the command tenant, run the body, and always restore the caller's context. */
  private void inTenantContext(String tenantId, ThrowingRunnable body) throws Exception {
    var context = ContextContainer.getInstance();
    var snapshot = context.snapshot();
    try {
      context.setTenant(tenantId);
      body.run();
    } finally {
      context.restore(snapshot);
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  protected abstract void dispatchCommand(OutboxCommand entry) throws Exception;

  protected void deadCommand(OutboxCommand entry) throws Exception {} // operator replay by default
}
