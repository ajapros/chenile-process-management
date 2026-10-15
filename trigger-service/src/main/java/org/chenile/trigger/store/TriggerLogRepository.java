package org.chenile.trigger.store;

import org.chenile.trigger.model.TriggerLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

public interface TriggerLogRepository extends JpaRepository<TriggerLog, String>, JpaSpecificationExecutor<TriggerLog> {
    Optional<TriggerLog> findByTriggerIdAndEventName(String triggerId, String eventName);
}
