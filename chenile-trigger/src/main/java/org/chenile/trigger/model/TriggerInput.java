package org.chenile.trigger.model;

import java.time.Instant;
import java.util.Map;

public class TriggerInput {
    public String triggerId;
    public String eventId;
    public Instant triggerTime = Instant.now();
    public Map<String, String> headers = new java.util.LinkedHashMap<>();
    public Object payload;
}
