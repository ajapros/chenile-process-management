package org.chenile.trigger.store;

import org.chenile.trigger.model.TriggerExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TriggerExecutionRepository extends JpaRepository<TriggerExecution, String>, JpaSpecificationExecutor<TriggerExecution> {}
