package org.chenile.orchestrator.process.outbox;

/**
 * Exponential backoff with a maximum interval and a maximum attempt count. On exhaustion the command
 * becomes DEAD (surfaced for alerting / compensation) rather than looping forever.
 */
public class OutboxRetryPolicy {
	public long initialIntervalMillis = 1000L;
	public double backoffCoefficient = 2.0;
	public long maxIntervalMillis = 60_000L;
	public int maxAttempts = 5;

	public static OutboxRetryPolicy defaults() {
		return new OutboxRetryPolicy();
	}

	/** Delay before the given (1-based) attempt may run again. */
	public long backoffMillis(int attempt) {
		if (attempt <= 1) {
			return initialIntervalMillis;
		}
		double delay = initialIntervalMillis * Math.pow(backoffCoefficient, attempt - 1);
		return (long) Math.min(delay, maxIntervalMillis);
	}

	public boolean isExhausted(int attempt) {
		return attempt >= maxAttempts;
	}
}
