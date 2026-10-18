package org.chenile.orchestrator.process.outbox;

import java.util.function.Consumer;

/** Infrastructure tests need only delivery envelopes, not process-service dependencies. */
final class OutboxTestSupport {
    static OutboxCommand emitCompleted(String process, String json) {
        return OutboxCommand.create(process, "EMIT_COMPLETED", process + ":completed", json);
    }
    static OutboxCommand startWorker(String process, String worker, String json) {
        return OutboxCommand.create(process, "START_WORKER", process + ":" + worker, json);
    }
    static OutboxCommand createSubProcess(String parent, String child, String json) {
        return OutboxCommand.create(parent, "CREATE_SUBPROCESS", "create:" + child, json);
    }
    static OutboxCommand signalParent(String child, String parent, String childId, String event, String json) {
        return OutboxCommand.create(child, "SIGNAL_PARENT", "signal:" + parent + ":" + childId + ":" + event, json);
    }
    static OutboxCommandLifecycle<Void> command(String type, Consumer<OutboxCommand> dispatch) {
        return command(type, dispatch, ignored -> { });
    }
    static OutboxCommandLifecycle<Void> command(String type, Consumer<OutboxCommand> dispatch, Consumer<OutboxCommand> dead) {
        return new OutboxCommandLifecycle<>() {
            public String type() { return type; }
            public void enqueue(Void ignored) { }
            public void dispatch(OutboxCommand entry) { dispatch.accept(entry); }
            public void onDead(OutboxCommand entry) { dead.accept(entry); }
        };
    }
}
