package org.chenile.trigger.cron;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.quartz.Scheduler;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JsonCrontabRepositoryTest {
    private static final String CRONS = """
        [{"name":"daily","tenant":"default","enabled":false,"cronExpression":"0 0 2 * * ?",
          "eventName":"ProcessCreate","payload":{"processDefName":"feed","args":{"batchId":"a"}},
          "headers":{"x-custom":"value"}}]
        """;
    private ByteArrayInputStream json(String value) { return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)); }
    @Test void readsNativeSchedulesAndStructuredPayloadsWithStableIds() throws Exception {
        var repository = new JsonCrontabRepository(); repository.read(json(CRONS));
        var cron = repository.findAll().get(0);
        assertEquals("UTC", cron.timezone);
        assertTrue(cron.eventPayloadJson.contains("processDefName"));
        assertEquals("{\"x-custom\":\"value\"}", cron.headersJson);
        assertTrue(repository.findByEnabledTrue().isEmpty());
        String id = cron.id; cron.name = "caller mutation";
        assertEquals("daily", repository.findById(id).orElseThrow().name);
        repository.read(json(CRONS));
        assertEquals(id, repository.findAll().get(0).id);
    }
    @Test void supportsNativeJsonStringFieldsAndEnabledFiltering() throws Exception {
        var repository = new JsonCrontabRepository();
        var cron = new Crontab(); cron.id="native"; cron.name="native";
        cron.cronExpression="0 0 2 * * ?"; cron.eventName="Custom"; cron.eventPayloadJson="{\"value\":1}";
        repository.read(json(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.List.of(cron))));
        assertEquals("native", repository.findByEnabledTrue().get(0).id);
        assertEquals("{\"value\":1}", repository.findById("native").orElseThrow().eventPayloadJson);
    }
    @Test void invalidReloadPreservesThePreviousSnapshot() throws Exception {
        var repository = new JsonCrontabRepository(); repository.read(json(CRONS));
        assertThrows(IllegalArgumentException.class, () -> repository.read(json(CRONS.replace("0 0 2 * * ?", "invalid"))));
        assertEquals("daily", repository.findAll().get(0).name);
        assertThrows(java.io.IOException.class, () -> repository.read(json("[{\"name\":\"a\",\"name\":\"b\"}]")));
        assertEquals(1, repository.findAll().size());
    }
    @Test void rejectsDuplicateNamesIdsAmbiguousPayloadsAndInvalidTimezone() throws Exception {
        var repository = new JsonCrontabRepository();
        String entry = CRONS.trim().substring(1, CRONS.trim().length()-1);
        assertThrows(IllegalArgumentException.class, () -> repository.read(json("[" + entry + "," + entry + "]")));
        assertThrows(IllegalArgumentException.class, () -> repository.read(json(
                CRONS.replace("\"payload\":", "\"eventPayloadJson\":\"{}\",\"payload\":"))));
        assertThrows(Exception.class, () -> repository.read(json(
                CRONS.replace("\"enabled\":false", "\"enabled\":false,\"timezone\":\"Not/AZone\""))));
        assertThrows(IllegalArgumentException.class, () -> repository.read(json("null")));
    }
    @Test void schedulerCanUseJsonRepositoryWithoutDatabaseRepository() throws Exception {
        var repository = new JsonCrontabRepository(); repository.read(json(CRONS.replace("false","true")));
        Scheduler scheduler = mock(Scheduler.class); when(scheduler.isStarted()).thenReturn(true);
        new CrontabScheduler(repository, mock(org.chenile.trigger.TriggerService.class), scheduler).schedulePersistedCrontabs();
        verify(scheduler).scheduleJob(any(org.quartz.JobDetail.class), any(org.quartz.Trigger.class));
    }
}
