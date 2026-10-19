package org.chenile.trigger.cron;

import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;
import org.chenile.core.context.ContextContainer;
import org.chenile.base.exception.BadRequestException;
import org.chenile.base.exception.NotFoundException;

public class DatabaseCrontabService implements CrontabService {
    private final CrontabRepository repository;
    private final CrontabScheduler scheduler;
    public DatabaseCrontabService(CrontabRepository repository, CrontabScheduler scheduler) {
        this.repository = repository; this.scheduler = scheduler;
    }
    @Override @Transactional
    public Crontab save(Crontab crontab) {
        try { scheduler.validate(crontab); }
        catch (IllegalArgumentException | java.time.DateTimeException invalid) {
            throw new BadRequestException("INVALID_CRONTAB", invalid.getMessage());
        }
        if (crontab.tenant != null && !Objects.equals(crontab.tenant, tenant()))
            throw new BadRequestException("INVALID_CRONTAB_TENANT", "Crontab tenant must match caller context");
        if (crontab.id != null) repository.findById(crontab.id).ifPresent(existing -> {
            if (!Objects.equals(existing.tenant, tenant())) throw notFound();
        });
        Crontab saved = repository.saveAndFlush(crontab);
        afterCommit(() -> {
            try { scheduler.schedule(saved); } catch (Exception e) { throw new IllegalStateException("Cannot schedule committed crontab; retry the update", e); }
        });
        return saved;
    }
    @Override @Transactional
    public void delete(String id) {
        repository.delete(retrieve(id));
        afterCommit(() -> {
            try { scheduler.unschedule(id); } catch (Exception e) { throw new IllegalStateException("Cannot remove committed crontab schedule", e); }
        });
    }
    @Override @Transactional(readOnly = true)
    public Crontab retrieve(String id) {
        return repository.findById(id).filter(cron -> Objects.equals(cron.tenant, tenant()))
                .orElseThrow(DatabaseCrontabService::notFound);
    }
    @Override @Transactional(readOnly = true)
    public List<Crontab> findAll() {
        return repository.findAll((root, query, cb) -> cb.equal(root.get("tenant"), tenant()));
    }
    private static String tenant() { return ContextContainer.getInstance().getTenant(); }
    private static NotFoundException notFound() {
        return new NotFoundException("CRONTAB_NOT_FOUND", "Crontab not found in this tenant");
    }
    private void afterCommit(Runnable change) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) { change.run(); return; }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { change.run(); }
            });
    }
}
