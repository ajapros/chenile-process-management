package org.chenile.orchestrator.process.outbox;

/**
 * All behavior for a command type, including its JSON schema and idempotency keys.
 * enqueue joins the producer transaction; dispatch joins the fenced handler transaction.
 * onDead runs after DEAD has committed, outside the failed transaction. It is best-effort:
 * implementations performing mutations must establish their own transaction.
 */
public interface OutboxCommandLifecycle<C> {
    String type();
    default boolean shouldEnqueue(C context) { return true; }
    void enqueue(C context) throws Exception;
    /** Persisted invocation headers only; the execution boundary owns installing and restoring context. */
    default java.util.Map<String, String> executionHeaders(OutboxCommand command) throws Exception {
        return java.util.Map.of();
    }
    void dispatch(OutboxCommand command) throws Exception;
    default void onDead(OutboxCommand command) throws Exception { }
}
