package org.chenile.trigger.cron;

import jakarta.persistence.*;
import org.chenile.jpautils.entity.BaseJpaEntity;

@Entity
@Table(name = "chenile_crontab", indexes = @Index(name = "idx_chenile_crontab_enabled", columnList = "enabled"))
public class Crontab extends BaseJpaEntity {
    @Column(nullable = false, unique = true) public String name;
    @Column(nullable = false) public boolean enabled = true;
    @Column(name = "cron_expression", nullable = false) public String cronExpression;
    @Column(nullable = false) public String timezone = "UTC";
    @Column(name = "event_name", nullable = false) public String eventName;
    @Column(name = "headers_json", columnDefinition = "TEXT") public String headersJson;
    @Column(name = "event_payload_json", columnDefinition = "TEXT") public String eventPayloadJson;
}
