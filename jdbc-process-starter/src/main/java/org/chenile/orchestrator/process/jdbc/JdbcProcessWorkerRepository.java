package org.chenile.orchestrator.process.jdbc;

import org.chenile.orchestrator.process.model.WorkerDto;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class JdbcProcessWorkerRepository {
	private final JdbcTemplate jdbcTemplate;
	private final TransactionTemplate transactionTemplate;

	public JdbcProcessWorkerRepository(JdbcTemplate jdbcTemplate,
			PlatformTransactionManager transactionManager) {
		this.jdbcTemplate = jdbcTemplate;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public boolean enqueue(WorkerDto workerDto, String payload) {
		Instant now = Instant.now();
		try {
			jdbcTemplate.update("""
					insert into chenile_process_work_item
					(id, process_id, process_type, worker_type, idempotency_key, payload, status, attempt, created_at, updated_at)
					values (?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?)
					""",
					UUID.randomUUID().toString(),
					workerDto.process.getId(),
					workerDto.process.processType,
					workerDto.workerType.name(),
					idempotencyKey(workerDto),
					payload,
					Timestamp.from(now),
					Timestamp.from(now));
			return true;
		} catch (DuplicateKeyException e) {
			return false;
		}
	}

	public Optional<JdbcProcessWorkItem> claimNext(String workerId, int lockSeconds, int maxAttempts) {
		return transactionTemplate.execute(status -> {
			Instant now = Instant.now();
			Instant lockedUntil = now.plusSeconds(lockSeconds);
			Optional<JdbcProcessWorkItem> item = jdbcTemplate.query("""
					select id, process_id, process_type, worker_type, idempotency_key, payload, status, attempt,
					       locked_by, locked_until, created_at, updated_at, finished_at, error_message
					  from chenile_process_work_item
					 where (status = 'PENDING' or (status = 'RUNNING' and locked_until < ?))
					   and attempt < ?
					 order by created_at
					 limit 1
					 for update skip locked
					""", rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(),
					Timestamp.from(now), maxAttempts);
			if (item.isEmpty()) {
				return Optional.empty();
			}
			JdbcProcessWorkItem workItem = item.get();
			int nextAttempt = workItem.attempt + 1;
			jdbcTemplate.update("""
					update chenile_process_work_item
					   set status = 'RUNNING', attempt = ?, locked_by = ?, locked_until = ?, updated_at = ?,
					       error_message = null
					 where id = ?
					""",
					nextAttempt,
					workerId,
					Timestamp.from(lockedUntil),
					Timestamp.from(now),
					workItem.id);
			workItem.status = "RUNNING";
			workItem.attempt = nextAttempt;
			workItem.lockedBy = workerId;
			workItem.lockedUntil = lockedUntil;
			workItem.updatedAt = now;
			workItem.errorMessage = null;
			return Optional.of(workItem);
		});
	}

	public void markSuccess(String id) {
		Instant now = Instant.now();
		jdbcTemplate.update("""
				update chenile_process_work_item
				   set status = 'SUCCESS', finished_at = ?, updated_at = ?, locked_until = null, error_message = null
				 where id = ?
				""", Timestamp.from(now), Timestamp.from(now), id);
	}

	public void markFailure(String id, int attempt, int maxAttempts, String errorMessage) {
		Instant now = Instant.now();
		String status = attempt >= maxAttempts ? "DEAD" : "PENDING";
		jdbcTemplate.update("""
				update chenile_process_work_item
				   set status = ?, finished_at = case when ? = 'DEAD' then ? else finished_at end,
				       updated_at = ?, locked_by = null, locked_until = null, error_message = ?
				 where id = ?
				""", status, status, Timestamp.from(now), Timestamp.from(now), trim(errorMessage), id);
	}

	public long backlogCount() {
		return jdbcTemplate.queryForObject("""
				select count(*)
				  from chenile_process_work_item
				 where status = 'PENDING'
				    or (status = 'RUNNING' and locked_until < ?)
				""", Long.class, Timestamp.from(Instant.now()));
	}

	public static String idempotencyKey(WorkerDto workerDto) {
		return workerDto.process.getId() + ":" + workerDto.process.processType + ":" + workerDto.workerType.name();
	}

	private JdbcProcessWorkItem map(ResultSet rs) throws SQLException {
		JdbcProcessWorkItem item = new JdbcProcessWorkItem();
		item.id = rs.getString("id");
		item.processId = rs.getString("process_id");
		item.processType = rs.getString("process_type");
		item.workerType = rs.getString("worker_type");
		item.idempotencyKey = rs.getString("idempotency_key");
		item.payload = rs.getString("payload");
		item.status = rs.getString("status");
		item.attempt = rs.getInt("attempt");
		item.lockedBy = rs.getString("locked_by");
		item.lockedUntil = toInstant(rs.getTimestamp("locked_until"));
		item.createdAt = toInstant(rs.getTimestamp("created_at"));
		item.updatedAt = toInstant(rs.getTimestamp("updated_at"));
		item.finishedAt = toInstant(rs.getTimestamp("finished_at"));
		item.errorMessage = rs.getString("error_message");
		return item;
	}

	private Instant toInstant(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toInstant();
	}

	private String trim(String message) {
		if (message == null) return null;
		return message.length() <= 4000 ? message : message.substring(0, 4000);
	}
}
