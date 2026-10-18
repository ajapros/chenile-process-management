package org.chenile.orchestrator.process.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Atomic enqueue plus fenced, recoverable PostgreSQL dispatch leases. */
public class ProcessOutboxRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate independent;

    public ProcessOutboxRepository(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        independent = new TransactionTemplate(manager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean enqueue(OutboxCommand command) {
        requireTransaction();
        Instant now = Instant.now();
        if (command.id == null) command.id = UUID.randomUUID().toString();
        return jdbc.update("""
                insert into chenile_process_outbox
                (id, process_id, command_type, payload,
                 idempotency_key, status, attempt, visible_after, created_at, updated_at, tenant_id)
                values (?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?, ?)
                on conflict do nothing
                """, command.id, command.processId, command.commandType, command.payload,
                command.idempotencyKey, timestamp(command.visibleAfter == null ? now : command.visibleAfter),
                timestamp(now), timestamp(now), command.tenantId) == 1;
    }

    public Optional<OutboxCommand> claimNext(String workerId, int lockSeconds, int maxAttempts) {
        if (lockSeconds < 1 || maxAttempts < 1) throw new IllegalArgumentException("Positive lease and maxAttempts required");
        return independent.execute(tx -> {
            Instant now = Instant.now();
            jdbc.update("""
                    update chenile_process_outbox set status='DEAD', finished_at=?, updated_at=?,
                      locked_by=null, locked_until=null, claim_token=null,
                      error_message='Lease expired after final attempt'
                    where id in (select id from chenile_process_outbox
                      where status='RUNNING' and locked_until < ? and attempt >= ?
                      for update skip locked)
                    """, timestamp(now), timestamp(now), timestamp(now), maxAttempts);
            Optional<OutboxCommand> found = jdbc.query("""
                    select * from chenile_process_outbox
                    where (status='PENDING' or (status='RUNNING' and locked_until < ?))
                      and visible_after <= ? and attempt < ?
                    order by visible_after, created_at, id limit 1 for update skip locked
                    """, rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(),
                    timestamp(now), timestamp(now), maxAttempts);
            if (found.isEmpty()) return found;
            OutboxCommand command = found.get();
            command.attempt++;
            command.claimToken = UUID.randomUUID().toString();
            command.lockedBy = workerId;
            command.lockedUntil = now.plusSeconds(lockSeconds);
            command.status = "RUNNING";
            if (command.startedAt == null) command.startedAt = now;
            jdbc.update("""
                    update chenile_process_outbox set status='RUNNING', started_at=coalesce(started_at, ?), attempt=?, locked_by=?,
                      locked_until=?, claim_token=?, updated_at=?, error_message=null where id=?
                    """, timestamp(now), command.attempt, workerId, timestamp(command.lockedUntil), command.claimToken,
                    timestamp(now), command.id);
            return Optional.of(command);
        });
    }

    /** Local handler mutations and acknowledgement commit together. External effects still require deduplication. */
    public void handleAndComplete(OutboxCommand command, OutboxCommandLifecycle<?> handler) {
        independent.executeWithoutResult(tx -> {
            // Lock and validate ownership BEFORE local mutations. A stale claimant must never run a handler.
            Integer owned = jdbc.query("""
                    select attempt from chenile_process_outbox
                    where id=? and status='RUNNING' and claim_token=? for update
                    """, rs -> rs.next() ? rs.getInt(1) : null, command.id, command.claimToken);
            if (owned == null) throw new IllegalStateException("Outbox lease no longer owned: " + command.id);
            try { handler.dispatch(command); }
            catch (Exception exception) { throw new IllegalStateException("Outbox handler failed", exception); }
            if (!acknowledge(command)) throw new IllegalStateException("Outbox acknowledgement lost: " + command.id);
        });
    }

    public boolean markDone(OutboxCommand command) {
        return independent.execute(tx -> acknowledge(command));
    }

    private boolean acknowledge(OutboxCommand command) {
        Instant now = Instant.now();
        return jdbc.update("""
                update chenile_process_outbox set status='DONE', finished_at=?, updated_at=?,
                  locked_by=null, locked_until=null, claim_token=null, error_message=null
                where id=? and status='RUNNING' and claim_token=?
                """, timestamp(now), timestamp(now), command.id, command.claimToken) == 1;
    }

    public boolean markFailure(OutboxCommand command, OutboxRetryPolicy policy, String message) {
        Instant now = Instant.now();
        boolean dead = policy.isExhausted(command.attempt);
        return independent.execute(tx -> jdbc.update("""
                update chenile_process_outbox set status=?, visible_after=?, updated_at=?,
                  locked_by=null, locked_until=null, claim_token=null,
                  finished_at=case when ?='DEAD' then ? else finished_at end, error_message=?
                where id=? and status='RUNNING' and claim_token=?
                """, dead ? "DEAD" : "PENDING",
                timestamp(dead ? now : now.plusMillis(policy.backoffMillis(command.attempt))),
                timestamp(now), dead ? "DEAD" : "PENDING", timestamp(now), trim(message),
                command.id, command.claimToken) == 1);
    }

    /**
     * Atomically claims a consumer/key pair; true allows the first effect, false skips a duplicate.
     * The caller commits this receipt alongside the business mutation.
     */
    public boolean checkIdempotency(String consumer, String key) {
        requireTransaction();
        return jdbc.update("""
                insert into chenile_process_receipt (consumer, receipt_key, received_at)
                values (?, ?, ?) on conflict do nothing
                """, consumer, key, timestamp(Instant.now())) == 1;
    }

    public long backlogCount() {
        Instant now = Instant.now();
        return jdbc.queryForObject("""
                select count(*) from chenile_process_outbox
                where visible_after <= ? and (status='PENDING' or (status='RUNNING' and locked_until < ?))
                """, Long.class, timestamp(now), timestamp(now));
    }

    public long deadCount() {
        return jdbc.queryForObject("select count(*) from chenile_process_outbox where status='DEAD'", Long.class);
    }

    /** Explicit operator replay; consumers keep receipts, so already committed effects are not repeated. */
    public boolean replayDead(String id) {
        return independent.execute(tx -> jdbc.update("""
                update chenile_process_outbox set status='PENDING', attempt=0, visible_after=?,
                  updated_at=?, finished_at=null, error_message=null where id=? and status='DEAD'
                """, timestamp(Instant.now()), timestamp(Instant.now()), id) == 1);
    }

    public Optional<OutboxCommand> findById(String id) {
        return jdbc.query("select * from chenile_process_outbox where id=?",
                rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(), id);
    }

    public Optional<OutboxCommand> findByIdempotencyKey(String key) {
        return jdbc.query("select * from chenile_process_outbox where idempotency_key=?",
                rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(), key);
    }

    public record CommandPage(java.util.List<OutboxCommand> content, long totalElements,
            int page, int size, int totalPages) { }

    /** Bounded, tenant-scoped command history. JSON metadata is owned by the producer. */
    public CommandPage findCommands(String type, String tenant, String id, String status,
            String payloadFragment, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page or size");
        if (status != null && !java.util.Set.of("PENDING", "RUNNING", "DONE", "DEAD").contains(status))
            throw new IllegalArgumentException("Invalid command status");
        StringBuilder where = new StringBuilder(" where command_type=? and tenant_id is not distinct from ?");
        java.util.List<Object> parameters = new java.util.ArrayList<>();
        parameters.add(type); parameters.add(tenant);
        if (id != null && !id.isBlank()) { where.append(" and id=?"); parameters.add(id); }
        if (status != null && !status.isBlank()) { where.append(" and status=?"); parameters.add(status); }
        if (payloadFragment != null) {
            where.append(" and payload like ? escape '!'");
            parameters.add("%" + payloadFragment.replace("!", "!!").replace("%", "!%")
                    .replace("_", "!_") + "%");
        }
        long count = jdbc.queryForObject("select count(*) from chenile_process_outbox" + where,
                Long.class, parameters.toArray());
        parameters.add(size); parameters.add((long) page * size);
        var rows = jdbc.query("select * from chenile_process_outbox" + where
                + " order by created_at desc, id desc limit ? offset ?", (rs, row) -> map(rs), parameters.toArray());
        return new CommandPage(rows, count, page, size, (int) ((count + size - 1) / size));
    }

    public java.util.List<OutboxCommand> findByIds(String type, String tenant, java.util.Set<String> ids) {
        if (ids.isEmpty()) return java.util.List.of();
        if (ids.size() > 1000) throw new IllegalArgumentException("Too many command IDs");
        var parameters = new java.util.ArrayList<Object>();
        parameters.add(type); parameters.add(tenant); parameters.addAll(ids);
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbc.query("select * from chenile_process_outbox where command_type=? and tenant_id is not distinct from ?"
                + " and id in (" + placeholders + ") order by created_at, id", (rs, row) -> map(rs), parameters.toArray());
    }

    private OutboxCommand map(ResultSet rs) throws SQLException {
        OutboxCommand c = new OutboxCommand();
        c.id = rs.getString("id"); c.processId = rs.getString("process_id");
        c.commandType = rs.getString("command_type");
        c.payload = rs.getString("payload");
        c.idempotencyKey = rs.getString("idempotency_key"); c.status = rs.getString("status");
        c.attempt = rs.getInt("attempt"); c.lockedBy = rs.getString("locked_by");
        c.claimToken = rs.getString("claim_token"); c.tenantId = rs.getString("tenant_id");
        c.visibleAfter = instant(rs, "visible_after"); c.lockedUntil = instant(rs, "locked_until");
        c.startedAt = instant(rs, "started_at");
        c.createdAt = instant(rs, "created_at"); c.updatedAt = instant(rs, "updated_at");
        c.finishedAt = instant(rs, "finished_at"); c.errorMessage = rs.getString("error_message");
        return c;
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Outbox writes require a producer transaction");
    }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
    private static Timestamp timestamp(Instant value) { return Timestamp.from(value); }
    private static String trim(String value) { return value == null || value.length() <= 4000 ? value : value.substring(0, 4000); }
}
