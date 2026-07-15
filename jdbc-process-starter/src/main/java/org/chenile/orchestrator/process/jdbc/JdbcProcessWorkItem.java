package org.chenile.orchestrator.process.jdbc;

import java.time.Instant;

public class JdbcProcessWorkItem {
	public String id;
	public String processId;
	public String processType;
	public String workerType;
	public String idempotencyKey;
	public String payload;
	public String status;
	public int attempt;
	public String lockedBy;
	public Instant lockedUntil;
	public Instant createdAt;
	public Instant updatedAt;
	public Instant finishedAt;
	public String errorMessage;
}
