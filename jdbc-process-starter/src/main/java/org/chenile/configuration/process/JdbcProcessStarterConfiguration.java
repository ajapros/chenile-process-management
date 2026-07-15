package org.chenile.configuration.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.process.jdbc.JdbcProcessWorkerProperties;
import org.chenile.orchestrator.process.jdbc.JdbcProcessWorkerRepository;
import org.chenile.orchestrator.process.jdbc.JdbcProcessWorkerRunner;
import org.chenile.orchestrator.process.jdbc.JdbcProcessWorkerStarter;
import org.chenile.orchestrator.process.service.defs.PostSaveHook;
import org.chenile.orchestrator.process.utils.api.BatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class JdbcProcessStarterConfiguration {
	private static final Logger logger = LoggerFactory.getLogger(JdbcProcessStarterConfiguration.class);

	@Bean
	JdbcProcessWorkerProperties jdbcProcessWorkerProperties(
			@Value("${chenile.process.worker.jdbc.lock-seconds:300}") int lockSeconds,
			@Value("${chenile.process.worker.jdbc.max-attempts:5}") int maxAttempts,
			@Value("${chenile.process.worker.jdbc.worker-id:${HOSTNAME:local-process-worker}}") String workerId,
			@Value("${chenile.process.worker.jdbc.run-worker:false}") boolean runWorker,
			@Value("${chenile.process.worker.jdbc.run-once:false}") boolean runOnce,
			@Value("${chenile.process.worker.jdbc.poll-interval-millis:5000}") long pollIntervalMillis) {
		JdbcProcessWorkerProperties properties = new JdbcProcessWorkerProperties();
		properties.lockSeconds = lockSeconds;
		properties.maxAttempts = maxAttempts;
		properties.workerId = workerId;
		properties.runWorker = runWorker;
		properties.runOnce = runOnce;
		properties.pollIntervalMillis = pollIntervalMillis;
		return properties;
	}

	@Bean
	JdbcProcessWorkerRepository jdbcProcessWorkerRepository(JdbcTemplate jdbcTemplate,
			PlatformTransactionManager transactionManager) {
		return new JdbcProcessWorkerRepository(jdbcTemplate, transactionManager);
	}

	@Bean
	JdbcProcessWorkerStarter jdbcProcessWorkerStarter(@Qualifier("postSaveHook") PostSaveHook postSaveHook,
			JdbcProcessWorkerRepository repository, ObjectMapper objectMapper) {
		logger.info("Initializing JDBC process worker starter");
		JdbcProcessWorkerStarter workerStarter = new JdbcProcessWorkerStarter(repository, objectMapper);
		postSaveHook.setWorkerStarter(workerStarter);
		return workerStarter;
	}

	@Bean
	JdbcProcessWorkerRunner jdbcProcessWorkerRunner(JdbcProcessWorkerRepository repository,
			JdbcProcessWorkerProperties properties,
			ObjectProvider<BatchService<?>> batchServiceProvider,
			ObjectMapper objectMapper) {
		return new JdbcProcessWorkerRunner(repository, properties, batchServiceProvider, objectMapper);
	}
}
