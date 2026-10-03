package org.chenile.orchestrator.process.model;

/**
 * Transport-neutral event emitted when a process reaches a terminal state.
 * Also serves as a ProcessDto for definition-driven process chaining.
 */
public class ProcessCompletedEvent extends ProcessDto {
    public String processId;
    public String processType;
    public String parentId;
    public String tenantId;
    public String state;
    public String input;
    public String output;
    public boolean successful;
    /** ISO-8601 timestamp assigned when the command is generated, not when it is delivered. */
    public String generatedAt;
}
