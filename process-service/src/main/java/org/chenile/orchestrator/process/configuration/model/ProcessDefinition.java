package org.chenile.orchestrator.process.configuration.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persistent representation of a process definition. */
@Entity
@Table(name = "process_definition")
public class ProcessDefinition {
    @Id
    @Column(name = "process_type", nullable = false, updatable = false)
    public String processType;

    /** JSON representation of {@code ProcessDef}, retaining its extensible maps. */
    @Column(name = "definition", nullable = false, columnDefinition = "TEXT")
    public String definition;

    protected ProcessDefinition() {
    }

    public ProcessDefinition(String processType, String definition) {
        this.processType = processType;
        this.definition = definition;
    }
}
