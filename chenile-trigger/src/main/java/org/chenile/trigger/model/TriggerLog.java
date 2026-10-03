package org.chenile.trigger.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** One durable idempotency claim per trigger and event, not a process audit record. */
@Entity
@Table(name = "trigger_log", uniqueConstraints = @UniqueConstraint(
        name = "uk_trigger_log_trigger_event", columnNames = {"trigger_id", "event_name"}))
public class TriggerLog {
    @Id @Column(nullable = false, updatable = false) public String id;
    @Column(name = "trigger_id", nullable = false, updatable = false) public String triggerId;
    @Column(name = "event_name", nullable = false, updatable = false) public String eventName;
    @Column(name = "trigger_time", nullable = false, updatable = false) public Instant triggerTime;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public TriggerLogStatus status;

    @PrePersist void initialize() {
        if (id == null) id = UUID.randomUUID().toString();
        if (triggerTime == null) triggerTime = Instant.now();
    }
}
