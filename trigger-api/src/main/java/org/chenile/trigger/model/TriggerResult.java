package org.chenile.trigger.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.chenile.base.response.ResponseHeadersAware;
import java.util.List;
import java.util.Map;

/** Durable acceptance only: this ID identifies both the outbox command and the trigger. */
public record TriggerResult(String triggerId) implements ResponseHeadersAware {
    @Override
    @JsonIgnore
    public Map<String, List<String>> getResponseHeaders() {
        return Map.of("Location", List.of("/trigger/" + triggerId));
    }
}
