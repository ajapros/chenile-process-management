package org.chenile.trigger.cron;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.util.Optional;
import org.chenile.trigger.TriggerService;
import org.chenile.trigger.model.TriggerInput;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;

class CrontabSchedulerTest {
  @Test
  void crontabPassesItsConfiguredEventPayloadThroughUnchanged() {
    Crontab tab = new Crontab();
    tab.id = "nightly";
    tab.name = "nightly-import";
    tab.cronExpression = "0 0 * * * ?";
    tab.eventName = "ProcessCreate";
    tab.eventPayloadJson = "{\"processDefName\":\"importDef\",\"args\":{\"batch\":7}}";
    tab.tenant = "alpha";
    tab.headersJson = "{\"x-chenile-tenant-id\":\"spoofed-tenant\"}";
    CrontabRepository repository = mock(CrontabRepository.class);
    when(repository.findById("nightly")).thenReturn(Optional.of(tab));
    TriggerService triggerService = mock(TriggerService.class);
    CrontabScheduler scheduler =
        new CrontabScheduler(repository, triggerService, mock(Scheduler.class));
    JobExecutionContext context = mock(JobExecutionContext.class);
    when(context.getFireInstanceId()).thenReturn("fire-1");
    when(context.getFireTime()).thenReturn(new Date());
    Date scheduled = new Date(1_767_225_600_000L); // 2026-01-01T00:00:00Z
    when(context.getScheduledFireTime()).thenReturn(scheduled);
    when(context.getNextFireTime()).thenReturn(new Date());

    scheduler.fire("nightly", context);

    var input = ArgumentCaptor.forClass(TriggerInput.class);
    verify(triggerService).trigger(input.capture());
    assertEquals("ProcessCreate", input.getValue().eventId);
    assertSame(tab.eventPayloadJson, input.getValue().payload);
    assertEquals("crontab:nightly:2026-01-01T00:00:00Z", input.getValue().idempotencyKey,
        "the key must be the same on every node that fires this schedule");
    assertEquals("alpha", input.getValue().headers.get("x-chenile-tenant-id"));
    assertEquals("CRON", input.getValue().source);
    assertFalse(input.getValue().headers.containsKey("x-chenile-trigger-source"));
    assertEquals("nightly", input.getValue().headers.get("x-chenile-crontab-id"));
    assertEquals(input.getValue().headers, input.getValue().triggerContext);
    assertTrue(input.getValue().headers.keySet().stream().allMatch(key -> key.startsWith("x-")));
    assertEquals(
        "fire-1", input.getValue().triggerContext.get("x-chenile-quartz-fire-instance-id"));
    assertNotNull(input.getValue().triggerContext.get("x-chenile-quartz-scheduled-fire-time"));
    assertNotNull(input.getValue().triggerContext.get("x-chenile-quartz-fire-time"));
    assertNotNull(input.getValue().triggerContext.get("x-chenile-quartz-next-fire-time"));
    assertDoesNotThrow(() -> scheduler.validate(tab));
  }

  @Test
  void validatesHeadersButLeavesPayloadValidationToTheEventPipeline() {
    Crontab tab = new Crontab();
    tab.name = "bad-json";
    tab.cronExpression = "0 0 * * * ?";
    tab.eventName = "ProcessCreate";
    var scheduler =
        new CrontabScheduler(
            mock(CrontabRepository.class), mock(TriggerService.class), mock(Scheduler.class));
    tab.eventPayloadJson = "not-json";
    assertDoesNotThrow(() -> scheduler.validate(tab));
    tab.eventPayloadJson = "{}";
    tab.headersJson = "not-json";
    assertThrows(IllegalArgumentException.class, () -> scheduler.validate(tab));
    tab.headersJson = "{\"custom\":\"value\"}";
    assertThrows(IllegalArgumentException.class, () -> scheduler.validate(tab));
  }
}
