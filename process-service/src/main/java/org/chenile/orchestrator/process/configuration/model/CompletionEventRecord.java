package org.chenile.orchestrator.process.configuration.model;

import jakarta.persistence.*;

@Entity
@Table(name = "chenile_process_completion_event", indexes =
    @Index(name = "idx_completion_event_tenant_trigger", columnList = "tenant,trigger_id"))
public class CompletionEventRecord {
    @Id @Column(name = "process_id") public String processId;
    public String tenant;
    @Column(name = "trigger_id") public String triggerId;
    @Column(name = "generated_at", nullable = false) public String generatedAt;
    @Column(nullable = false, columnDefinition = "TEXT") public String payload;
}
