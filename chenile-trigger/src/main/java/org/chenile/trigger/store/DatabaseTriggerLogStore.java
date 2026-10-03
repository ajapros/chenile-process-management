package org.chenile.trigger.store;

import org.chenile.trigger.model.TriggerLog;
import org.chenile.trigger.model.TriggerLogStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

public class DatabaseTriggerLogStore implements TriggerLogStore {
    private final TriggerLogRepository repository;

    public DatabaseTriggerLogStore(TriggerLogRepository repository) { this.repository = repository; }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TriggerLog record(TriggerLog log) { return repository.saveAndFlush(log); }

    @Override
    public Optional<TriggerLog> findByTriggerIdAndEventName(String triggerId, String eventName) {
        return repository.findByTriggerIdAndEventName(triggerId, eventName);
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TriggerLog markCompleted(String logId) { return updateStatus(logId, TriggerLogStatus.COMPLETED); }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TriggerLog markFailed(String logId) { return updateStatus(logId, TriggerLogStatus.FAILED); }

    private TriggerLog updateStatus(String logId, TriggerLogStatus status) {
        TriggerLog log = repository.findById(logId).orElseThrow();
        log.status = status;
        return repository.saveAndFlush(log);
    }
}
