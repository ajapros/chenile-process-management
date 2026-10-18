package org.chenile.trigger.model;

/** Durable acceptance only: this ID identifies both the outbox command and the trigger. */
public record TriggerResult(String triggerId) { }
