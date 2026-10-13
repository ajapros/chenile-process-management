package org.chenile.trigger.cron;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.*;
import java.util.ArrayList;
import static org.mockito.Mockito.*;

class CrontabTransactionTest {
    @Test void quartzIsUpdatedOnlyAfterCommitAndNotOnRollback() throws Exception {
        CrontabRepository repository = mock(CrontabRepository.class);
        CrontabScheduler scheduler = mock(CrontabScheduler.class);
        Crontab tab = new Crontab(); tab.id = "transactional-tab";
        when(repository.saveAndFlush(tab)).thenReturn(tab);
        DatabaseCrontabService service = new DatabaseCrontabService(repository, scheduler);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.save(tab);
            verify(scheduler, never()).schedule(tab);
            var callbacks = new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
            callbacks.forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(scheduler, never()).schedule(tab);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.save(tab);
            verify(scheduler, never()).schedule(tab);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(scheduler).schedule(tab);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}
