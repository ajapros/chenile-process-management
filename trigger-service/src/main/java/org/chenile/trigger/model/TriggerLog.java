package org.chenile.trigger.model;

import jakarta.persistence.*;
import org.chenile.jpautils.entity.BaseJpaEntity;
import java.time.Instant;

/** One accepted trigger: durable idempotency identity and dispatch lifecycle metadata. */
@Entity
@Table(name = "trigger_log", uniqueConstraints = @UniqueConstraint(
        name = "uk_trigger_log_trigger_event", columnNames = {"trigger_id", "event_name"}), indexes = {
        @Index(name = "idx_trigger_log_tenant_time", columnList = "tenant,started_at"),
        @Index(name = "idx_trigger_log_tenant_trigger", columnList = "tenant,trigger_id")})
public class TriggerLog extends BaseJpaEntity {
    @Column(name = "trigger_id", nullable = false, updatable = false) public String triggerId;
    @Column(name = "event_name", nullable = false, updatable = false) public String eventName;
    @Column(name = "trigger_time", nullable = false, updatable = false) public Instant triggerTime;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public TriggerLogStatus status;

    public String source;
    @Column(name = "started_at") public Instant startedAt;
    @Column(name = "finished_at") public Instant finishedAt;
    @Column(columnDefinition = "TEXT") public String error;
    @Column(columnDefinition = "TEXT") public String payload;
    /**
     * JSON that indicates the exact context in which the trigger was invoked.
     */
    @Column(columnDefinition = "TEXT") public String triggerContext;

    @PrePersist void initialize() {
        if (triggerTime == null) triggerTime = Instant.now();
    }
}
