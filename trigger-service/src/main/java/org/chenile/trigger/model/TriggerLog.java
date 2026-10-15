package org.chenile.trigger.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** One accepted trigger: durable idempotency identity and dispatch lifecycle metadata. */
@Entity
@Table(name = "trigger_log", uniqueConstraints = @UniqueConstraint(
        name = "uk_trigger_log_trigger_event", columnNames = {"trigger_id", "event_name"}), indexes = {
        @Index(name = "idx_trigger_log_tenant_time", columnList = "tenant,started_at"),
        @Index(name = "idx_trigger_log_tenant_trigger", columnList = "tenant,trigger_id"),
        @Index(name = "idx_trigger_log_crontab", columnList = "tenant,crontab_id")})
public class TriggerLog {
    @Id @Column(nullable = false, updatable = false) public String id;
    @Column(name = "trigger_id", nullable = false, updatable = false) public String triggerId;
    @Column(name = "event_name", nullable = false, updatable = false) public String eventName;
    @Column(name = "trigger_time", nullable = false, updatable = false) public Instant triggerTime;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public TriggerLogStatus status;

    public String tenant;
    public String source;
    @Column(name = "crontab_id") public String crontabId;
    @Column(name = "started_at") public Instant startedAt;
    @Column(name = "finished_at") public Instant finishedAt;
    @Column(columnDefinition = "TEXT") public String error;
    @Column(columnDefinition = "TEXT") public String payload;

    @PrePersist void initialize() {
        if (id == null) id = UUID.randomUUID().toString();
        if (triggerTime == null) triggerTime = Instant.now();
    }
}
