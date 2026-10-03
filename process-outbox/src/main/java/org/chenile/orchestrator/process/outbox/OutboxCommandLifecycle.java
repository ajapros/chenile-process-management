package org.chenile.orchestrator.process.outbox;

/**
 * All behavior for a command type, including its JSON schema and idempotency keys.
 * enqueue joins the producer transaction; dispatch joins the fenced handler transaction.
 * onDead runs after DEAD has committed, outside the failed transaction. It is best-effort:
 * implementations performing mutations must establish their own transaction.
 */
public interface OutboxCommandLifecycle<C> {
    String type();
    void enqueue(C context) throws Exception;
    void dispatch(OutboxCommand command) throws Exception;
    default void onDead(OutboxCommand command) throws Exception { }
}
