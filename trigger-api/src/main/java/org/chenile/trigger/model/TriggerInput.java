package org.chenile.trigger.model;

import java.time.Instant;
import java.util.Map;

public class TriggerInput {
    /** Optional submission retry key; the accepted trigger ID is assigned by the outbox. */
    public String idempotencyKey;
    public String eventId;
    public Instant triggerTime = Instant.now();
    public Map<String, String> headers = new java.util.LinkedHashMap<>();
    /** Adapter-owned metadata, persisted as JSON without source-specific log columns. */
    public Map<String, Object> triggerContext = new java.util.LinkedHashMap<>();
    public Object payload;
}
