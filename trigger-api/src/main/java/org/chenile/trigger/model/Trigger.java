package org.chenile.trigger.model;

import java.time.Instant;
import java.util.Map;

/** Persisted event invocation, shared by trigger producers, dispatch, and status readers. */
public record Trigger(
    String eventId,
    Instant triggerTime,
    Map<String, String> headers,
    Map<String, Object> triggerContext,
    Object payload,
    String source) {
  public Trigger {
    if (source == null || source.isBlank()) source = "EXTERNAL";
  }
}
