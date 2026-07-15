package org.chenile.orchestrator.process.jdbc;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.WorkerType;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.sql.Connection;
import java.util.Optional;

public class JdbcProcessWorkerRepositoryTest {
	private JdbcProcessWorkerRepository repository;

	@Before
	public void setUp() throws Exception {
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:process_worker;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
		dataSource.setUser("sa");
		dataSource.setPassword("");
		try (Connection connection = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(connection, new ClassPathResource("chenile-process-work-schema.sql"));
			connection.createStatement().execute("delete from chenile_process_work_item");
		}
		PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
		repository = new JdbcProcessWorkerRepository(new JdbcTemplate(dataSource), transactionManager);
	}

	@Test
	public void enqueueIsIdempotent() throws Exception {
		WorkerDto workerDto = workerDto("p1", WorkerType.EXECUTOR);
		Assert.assertTrue(repository.enqueue(workerDto, "{}"));
		Assert.assertFalse(repository.enqueue(workerDto, "{}"));
		Assert.assertEquals(1L, repository.backlogCount());
	}

	@Test
	public void claimSkipsLockedWork() throws Exception {
		repository.enqueue(workerDto("p1", WorkerType.EXECUTOR), "{}");
		repository.enqueue(workerDto("p2", WorkerType.EXECUTOR), "{}");

		Optional<JdbcProcessWorkItem> first = repository.claimNext("worker-1", 300, 5);
		Optional<JdbcProcessWorkItem> second = repository.claimNext("worker-2", 300, 5);

		Assert.assertTrue(first.isPresent());
		Assert.assertTrue(second.isPresent());
		Assert.assertNotEquals(first.get().id, second.get().id);
	}

	@Test
	public void failureRetriesUntilMaxAttemptThenDead() throws Exception {
		repository.enqueue(workerDto("p1", WorkerType.EXECUTOR), "{}");
		JdbcProcessWorkItem item = repository.claimNext("worker-1", 300, 2).orElseThrow();
		repository.markFailure(item.id, item.attempt, 2, "first failure");
		Assert.assertEquals(1L, repository.backlogCount());

		item = repository.claimNext("worker-1", 300, 2).orElseThrow();
		repository.markFailure(item.id, item.attempt, 2, "second failure");
		Assert.assertEquals(0L, repository.backlogCount());
		Assert.assertTrue(repository.claimNext("worker-1", 300, 2).isEmpty());
	}

	private WorkerDto workerDto(String id, WorkerType workerType) {
		Process process = new Process();
		process.id = id;
		process.processType = "bulkUploadChunk";
		WorkerDto workerDto = new WorkerDto();
		workerDto.process = process;
		workerDto.workerType = workerType;
		return workerDto;
	}
}
