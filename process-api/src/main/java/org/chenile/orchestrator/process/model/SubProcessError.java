package org.chenile.orchestrator.process.model;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.chenile.jpautils.entity.BaseJpaEntity;

import java.util.Date;
import java.util.List;

@Entity
@Table(name = "subprocess_error")
public class SubProcessError extends BaseJpaEntity {
    public String processId;
    public Date timeOfCompletion;


    @ElementCollection
    public List<String> errors ;

    @Override
    public SubProcessError clone() {
        SubProcessError e1 = new SubProcessError();
        e1.errors = this.errors;
        e1.timeOfCompletion = this.timeOfCompletion;
        e1.processId = this.processId;
        return e1;
    }
}
