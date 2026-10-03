package org.chenile.orchestrator.process.outbox;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.util.Optional;

/**
 * The durable-execution guarantees: enqueue joins the caller's transaction (a rolled-back transition
 * leaves no orphan command), idempotent enqueue, retry backoff hides a failed command until due, and
 * exhaustion moves it to DEAD.
 */
public class ProcessOutboxRepositoryTest {
	private ProcessOutboxRepository repository;
	private TransactionTemplate txTemplate;

	@Before
	public void setUp() throws Exception {
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:process_outbox;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
		dataSource.setUser("sa");
		dataSource.setPassword("");
		try (Connection connection = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(connection, new ClassPathResource("chenile-process-outbox-schema.sql"));
			connection.createStatement().execute("delete from chenile_process_outbox");
		}
		PlatformTransactionManager tm = new DataSourceTransactionManager(dataSource);
		txTemplate = new TransactionTemplate(tm);
		repository = new ProcessOutboxRepository(new JdbcTemplate(dataSource), tm);
	}

	@Test
	public void enqueueRollsBackWithTheCallerTransaction() {
		// The whole point of an outbox: if the state-save transaction rolls back, the queued
		// consequence must vanish with it - no orphan, no lost command.
		try {
			txTemplate.executeWithoutResult(status -> {
				enqueue(OutboxTestSupport.startWorker("p1", "SPLITTER", "{}"));
				throw new IllegalStateException("simulated transition failure after enqueue");
			});
			Assert.fail("expected rollback");
		} catch (IllegalStateException expected) {
			// rolled back
		}
		Assert.assertEquals(0L, repository.backlogCount());
	}

	@Test
	public void enqueueIsIdempotent() {
		Assert.assertTrue(enqueue(OutboxTestSupport.emitCompleted("p1", "{}")));
		Assert.assertFalse(enqueue(OutboxTestSupport.emitCompleted("p1", "{}")));
		Assert.assertEquals(1L, repository.backlogCount());
	}

	@Test
	public void distinctCommandTypesForOneProcessDoNotCollide() {
		Assert.assertTrue(enqueue(OutboxTestSupport.startWorker("p1", "SPLITTER", "{}")));
		Assert.assertTrue(enqueue(OutboxTestSupport.emitCompleted("p1", "{}")));
		Assert.assertTrue(enqueue(OutboxTestSupport.signalParent("c1", "p1", "c1", "subProcessDoneSuccessfully", "{}")));
		Assert.assertEquals(3L, repository.backlogCount());
	}

	@Test
	public void activeLeasesPreventClaimingTheSameCommandAgain() {
		enqueue(OutboxTestSupport.startWorker("p1", "SPLITTER", "{}"));
		enqueue(OutboxTestSupport.startWorker("p2", "SPLITTER", "{}"));
		Optional<OutboxCommand> first = repository.claimNext("w1", 300, 5);
		Optional<OutboxCommand> second = repository.claimNext("w2", 300, 5);
		Assert.assertTrue(first.isPresent());
		Assert.assertTrue(second.isPresent());
		Assert.assertNotEquals(first.get().id, second.get().id);
		Assert.assertTrue(repository.claimNext("w3", 300, 5).isEmpty());
	}

	@Test
	public void failureBacksOffAndHidesUntilDue() {
		enqueue(OutboxTestSupport.emitCompleted("p1", "{}"));
		OutboxCommand claimed = repository.claimNext("w1", 300, 5).orElseThrow();
		OutboxRetryPolicy policy = new OutboxRetryPolicy();
		policy.initialIntervalMillis = 60_000L;
		repository.markFailure(claimed, policy, "boom");
		Assert.assertEquals(0L, repository.backlogCount());
		Assert.assertTrue(repository.claimNext("w1", 300, 5).isEmpty());
	}

	@Test
	public void handlerFailureRollsBackLocalMutationsAndKeepsCommandRetryable() {
		// Pins the "local handler mutations and acknowledgement commit together" invariant: a receipt
		// written by a handler that then throws must roll back WITH the (never-reached) acknowledgement,
		// and the command must remain retryable rather than being lost or marked DONE.
		enqueue(OutboxTestSupport.emitCompleted("p1", "{}"));
		OutboxCommand claimed = repository.claimNext("w1", 300, 5).orElseThrow();
		OutboxCommandLifecycle<Void> failing = OutboxTestSupport.command("EMIT_COMPLETED", cmd -> {
			repository.receive("TEST", "k1"); // a local mutation inside the dispatch transaction
			throw new RuntimeException("boom");
		});
		try {
			repository.handleAndComplete(claimed, failing);
			Assert.fail("expected the handler failure to propagate");
		} catch (RuntimeException expected) {
			// handleAndComplete rolled back
		}
		// The receipt must be gone: a fresh insert of the same key succeeds (true == freshly inserted).
		Assert.assertTrue(txTemplate.execute(tx -> repository.receive("TEST", "k1")));
		// The command was never acknowledged; the dispatcher's failure path now makes it retryable.
		OutboxRetryPolicy policy = new OutboxRetryPolicy();
		policy.initialIntervalMillis = 0L;
		Assert.assertTrue(repository.markFailure(claimed, policy, "boom"));
		Assert.assertEquals(1L, repository.backlogCount());
	}

    private boolean enqueue(OutboxCommand command) {
        return txTemplate.execute(tx -> repository.enqueue(command));
    }
}
