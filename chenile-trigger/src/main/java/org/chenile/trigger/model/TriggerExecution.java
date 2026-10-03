package org.chenile.trigger.model;

import jakarta.persistence.*;
import java.time.Instant;

/** One accepted trigger dispatch, not an idempotency claim or a process audit log. */
@Entity
@Table(name = "chenile_trigger_execution", indexes = {
    @Index(name = "idx_trigger_execution_tenant_time", columnList = "tenant,started_at"),
    @Index(name = "idx_trigger_execution_trigger", columnList = "tenant,trigger_id")})
public class TriggerExecution {
    @Id public String id;
    public String tenant;
    @Column(name = "trigger_id", nullable = false) public String triggerId;
    @Column(name = "event_name", nullable = false) public String eventName;
    public String source;
    @Column(name = "crontab_id") public String crontabId;
    @Column(name = "trigger_time", nullable = false) public Instant triggerTime;
    @Column(name = "started_at", nullable = false) public Instant startedAt;
    @Column(name = "finished_at") public Instant finishedAt;
    public String status;
    @Column(columnDefinition = "TEXT") public String error;
    @Column(columnDefinition = "TEXT") public String payload;
}
