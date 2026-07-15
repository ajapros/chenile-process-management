package org.chenile.orchestrator.process.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.utils.api.BatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;

public class JdbcProcessWorkerRunner implements CommandLineRunner {
	private static final Logger logger = LoggerFactory.getLogger(JdbcProcessWorkerRunner.class);
	private final JdbcProcessWorkerRepository repository;
	private final JdbcProcessWorkerProperties properties;
	private final ObjectProvider<BatchService<?>> batchServiceProvider;
	private final ObjectMapper objectMapper;

	public JdbcProcessWorkerRunner(JdbcProcessWorkerRepository repository,
			JdbcProcessWorkerProperties properties,
			ObjectProvider<BatchService<?>> batchServiceProvider,
			ObjectMapper objectMapper) {
		this.repository = repository;
		this.properties = properties;
		this.batchServiceProvider = batchServiceProvider;
		this.objectMapper = objectMapper;
	}

	@Override
	public void run(String... args) throws Exception {
		if (!properties.runWorker) {
			return;
		}
		do {
			boolean processed = processOne();
			if (properties.runOnce) {
				return;
			}
			if (!processed) {
				Thread.sleep(properties.pollIntervalMillis);
			}
		} while (true);
	}

	public boolean processOne() {
		return repository.claimNext(properties.workerId, properties.lockSeconds, properties.maxAttempts)
				.map(this::execute)
				.orElse(false);
	}

	private boolean execute(JdbcProcessWorkItem item) {
		try {
			BatchService<?> batchService = batchServiceProvider.getIfAvailable();
			if (batchService == null) {
				throw new IllegalStateException("No BatchService bean available for JDBC process worker execution");
			}
			WorkerDto workerDto = objectMapper.readValue(item.payload, WorkerDto.class);
			boolean started = batchService.startWorker(workerDto);
			if (!started) {
				throw new IllegalStateException("BatchService did not start worker for " + item.idempotencyKey);
			}
			repository.markSuccess(item.id);
			return true;
		} catch (Exception e) {
			logger.error("Process work failed id={} idempotencyKey={} attempt={}/{}",
					item.id, item.idempotencyKey, item.attempt, properties.maxAttempts, e);
			repository.markFailure(item.id, item.attempt, properties.maxAttempts, e.getMessage());
			return true;
		}
	}
}
