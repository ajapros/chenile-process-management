package org.chenile.trigger.cron;

import org.chenile.trigger.TriggerService;
import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CrontabSchedulerTest {
    @Test
    void crontabPassesItsConfiguredEventPayloadThroughUnchanged() {
        Crontab tab = new Crontab();
        tab.id = "nightly"; tab.name = "nightly-import"; tab.cronExpression = "0 0 * * * ?";
        tab.eventName = "ProcessCreate"; tab.eventPayloadJson = "{\"processDefName\":\"importDef\",\"args\":{\"batch\":7}}";
        tab.tenant = "alpha";
        tab.headersJson = "{\"x-chenile-tenant-id\":\"spoofed-tenant\"}";
        CrontabRepository repository = mock(CrontabRepository.class);
        when(repository.findById("nightly")).thenReturn(Optional.of(tab));
        TriggerService triggerService = mock(TriggerService.class);
        CrontabScheduler scheduler = new CrontabScheduler(repository, triggerService, mock(Scheduler.class));
        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getFireInstanceId()).thenReturn("fire-1");
        when(context.getFireTime()).thenReturn(new java.util.Date());
        when(context.getScheduledFireTime()).thenReturn(new java.util.Date());

        scheduler.fire("nightly", context);

        var input = org.mockito.ArgumentCaptor.forClass(org.chenile.trigger.model.TriggerInput.class);
        verify(triggerService).trigger(input.capture());
        assertEquals("ProcessCreate", input.getValue().eventId);
        assertEquals("importDef", ((java.util.Map<?, ?>) input.getValue().payload).get("processDefName"));
        assertEquals("crontab:nightly:fire-1", input.getValue().triggerId);
        assertEquals("alpha", input.getValue().headers.get("x-chenile-tenant-id"));
        assertEquals("nightly", input.getValue().headers.get("chenile-crontab-id"));
        assertDoesNotThrow(() -> scheduler.validate(tab));
    }

    @Test void validatesPayloadAndHeadersBeforePersistingSchedules() {
        Crontab tab = new Crontab(); tab.name = "bad-json"; tab.cronExpression = "0 0 * * * ?"; tab.eventName = "ProcessCreate";
        var scheduler = new CrontabScheduler(mock(CrontabRepository.class), mock(TriggerService.class), mock(Scheduler.class));
        tab.eventPayloadJson = "not-json";
        assertThrows(IllegalArgumentException.class, () -> scheduler.validate(tab));
        tab.eventPayloadJson = "{}"; tab.headersJson = "not-json";
        assertThrows(IllegalArgumentException.class, () -> scheduler.validate(tab));
    }
}
