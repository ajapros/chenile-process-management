package org.chenile.trigger.store;

import org.chenile.trigger.model.TriggerLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TriggerLogRepository extends JpaRepository<TriggerLog, String> {
    Optional<TriggerLog> findByTriggerIdAndEventName(String triggerId, String eventName);
}
