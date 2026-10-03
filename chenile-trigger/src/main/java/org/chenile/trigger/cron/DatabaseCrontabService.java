package org.chenile.trigger.cron;

import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public class DatabaseCrontabService implements CrontabService {
    private final CrontabRepository repository;
    private final CrontabScheduler scheduler;
    public DatabaseCrontabService(CrontabRepository repository, CrontabScheduler scheduler) {
        this.repository = repository; this.scheduler = scheduler;
    }
    @Override @Transactional
    public Crontab save(Crontab crontab) {
        scheduler.validate(crontab);
        Crontab saved = repository.saveAndFlush(crontab);
        afterCommit(() -> {
            try { scheduler.schedule(saved); } catch (Exception e) { throw new IllegalStateException("Cannot schedule committed crontab; retry the update", e); }
        });
        return saved;
    }
    @Override @Transactional
    public void delete(String id) {
        repository.deleteById(id);
        afterCommit(() -> {
            try { scheduler.unschedule(id); } catch (Exception e) { throw new IllegalStateException("Cannot remove committed crontab schedule", e); }
        });
    }
    @Override public List<Crontab> findAll() { return repository.findAll(); }
    private void afterCommit(Runnable change) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) { change.run(); return; }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { change.run(); }
            });
    }
}
