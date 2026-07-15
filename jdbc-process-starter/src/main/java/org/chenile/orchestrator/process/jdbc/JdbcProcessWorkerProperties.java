package org.chenile.orchestrator.process.jdbc;

public class JdbcProcessWorkerProperties {
	public int lockSeconds = 300;
	public int maxAttempts = 5;
	public String workerId = "local-process-worker";
	public boolean runWorker = false;
	public boolean runOnce = false;
	public long pollIntervalMillis = 5000L;
}
