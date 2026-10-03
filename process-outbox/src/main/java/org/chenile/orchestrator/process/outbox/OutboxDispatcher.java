package org.chenile.orchestrator.process.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drains the outbox: claims a due command, runs it through the {@link OutboxCommandLifecycle}, and marks
 * it DONE or (on failure) applies the {@link OutboxRetryPolicy}.
 *
 * <p>{@link #processOne} does one command; {@link #drainAll} loops until nothing is due. Production
 * runs {@code processOne} behind a poll loop (or scales replicas off {@link
 * ProcessOutboxRepository#backlogCount()}); in-VM / test flows call {@code drainAll} on the calling
 * thread right after the state-save transaction commits, preserving today's synchronous cascade
 * semantics so the existing tests keep working.
 */
public class OutboxDispatcher {
	private static final Logger logger = LoggerFactory.getLogger(OutboxDispatcher.class);

	private final ProcessOutboxRepository repository;
	private final OutboxCommandRegistry<?> commands;
	private final OutboxRetryPolicy retryPolicy;
	private final String workerId;
	private final int lockSeconds;

	public OutboxDispatcher(ProcessOutboxRepository repository, OutboxCommandRegistry<?> commands,
			OutboxRetryPolicy retryPolicy, String workerId, int lockSeconds) {
		if (retryPolicy.maxAttempts < 1 || lockSeconds < 1)
			throw new IllegalArgumentException("Positive outbox maxAttempts and lease duration required");
		this.repository = repository;
		this.commands = commands;
		this.retryPolicy = retryPolicy;
		this.workerId = workerId;
		this.lockSeconds = lockSeconds;
	}

	/** @return true if a command was claimed (success or failure), false if none was due. */
	public boolean processOne() {
		return repository.claimNext(workerId, lockSeconds, retryPolicy.maxAttempts)
				.map(this::execute)
				.orElse(false);
	}

	/**
	 * Drain until nothing is immediately due. Used for synchronous in-VM execution. The bound guards
	 * against a pathological command that keeps re-enqueuing itself.
	 */
	public int drainAll(int maxCommands) {
		int processed = 0;
		while (processed < maxCommands && processOne()) {
			processed++;
		}
		return processed;
	}

	private boolean execute(OutboxCommand command) {
        OutboxCommandLifecycle<?> lifecycle = null;
		try {
            lifecycle = commands.resolve(command.commandType);
			repository.handleAndComplete(command, lifecycle);
			return true;
		} catch (Exception e) {
			logger.error("Outbox command failed id={} key={} type={} attempt={}/{}",
					command.id, command.idempotencyKey, command.commandType, command.attempt,
					retryPolicy.maxAttempts, e);
			boolean dead = retryPolicy.isExhausted(command.attempt);
			// markFailure returns true only for the owning claimant (claim_token match); a fenced-out
			// stale claimant updates nothing and must NOT drive compensation.
			boolean owned = repository.markFailure(command, retryPolicy, e.getMessage());
			if (dead && owned && lifecycle != null) {
				try {
					lifecycle.onDead(command);
				} catch (Exception compensationFailure) {
					logger.error("Outbox dead-letter compensation failed id={} key={}",
							command.id, command.idempotencyKey, compensationFailure);
				}
			}
			return true;
		}
	}
}
