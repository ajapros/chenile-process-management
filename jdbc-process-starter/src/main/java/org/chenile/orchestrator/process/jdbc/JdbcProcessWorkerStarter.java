package org.chenile.orchestrator.process.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbcProcessWorkerStarter implements WorkerStarter {
	private static final Logger logger = LoggerFactory.getLogger(JdbcProcessWorkerStarter.class);
	private final JdbcProcessWorkerRepository repository;
	private final ObjectMapper objectMapper;

	public JdbcProcessWorkerStarter(JdbcProcessWorkerRepository repository, ObjectMapper objectMapper) {
		this.repository = repository;
		this.objectMapper = objectMapper;
	}

	@Override
	public void start(WorkerDto workerDto) {
		try {
			String payload = objectMapper.writeValueAsString(workerDto);
			boolean inserted = repository.enqueue(workerDto, payload);
			if (inserted) {
				logger.info("Enqueued process work processId={} processType={} workerType={}",
						workerDto.process.getId(), workerDto.process.processType, workerDto.workerType);
			} else {
				logger.info("Skipped duplicate process work idempotencyKey={}",
						JdbcProcessWorkerRepository.idempotencyKey(workerDto));
			}
		} catch (Exception e) {
			throw new IllegalStateException("Unable to enqueue process work for " + workerDto.process.getId(), e);
		}
	}
}
