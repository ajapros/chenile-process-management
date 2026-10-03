package org.chenile.trigger.model;

public record TriggerResult(String triggerId, String eventId, String logId, boolean dispatched, boolean duplicate) { }
