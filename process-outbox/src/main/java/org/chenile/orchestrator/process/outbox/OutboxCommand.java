package org.chenile.orchestrator.process.outbox;

import java.time.Instant;

/** Generic persisted delivery envelope. Each registered lifecycle owns the JSON payload schema. */
public class OutboxCommand {
	public String id;
	public String processId;
	public String commandType;
	public String payload;
	public String idempotencyKey;
	public String status;
	public int attempt;
	public Instant visibleAfter;
	public String lockedBy;
	public String claimToken;
	public String tenantId;
	public Instant lockedUntil;
	public Instant createdAt;
	public Instant updatedAt;
	public Instant finishedAt;
	public String errorMessage;
    private transient Object inlinePayload;

    /** Local execution preserves object identity; this body is never written to the table. */
    public void attachInlinePayload(Object body) { inlinePayload = body; }
    public <T> T inlinePayload(Class<T> type) { return inlinePayload == null ? null : type.cast(inlinePayload); }

    public static OutboxCommand create(String processId, String type, String key, String json) {
        OutboxCommand command = new OutboxCommand();
        command.processId = processId;
        command.commandType = type;
        command.idempotencyKey = key;
        command.payload = json;
        return command;
    }
}
