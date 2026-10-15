package org.chenile.trigger.store;

import org.chenile.trigger.model.TriggerLog;
import org.chenile.trigger.model.TriggerLogStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TriggerLogStoreTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:trigger-log;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
class TriggerLogStoreTest {
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = TriggerLog.class)
    @EnableJpaRepositories(basePackageClasses = TriggerLogRepository.class)
    @Import(DatabaseTriggerLogStore.class)
    static class Config { }

    @Autowired TriggerLogStore store;

    @Test
    void databaseRejectsDuplicateTriggerEventPairsAndPersistsTheirOutcome() {
        TriggerLog log = store.record(log("trigger-1", "EventA"));
        assertNotNull(log.id);
        assertThrows(DataIntegrityViolationException.class,
                () -> store.record(log("trigger-1", "EventA")));
        assertNotNull(store.markRunning(log.id).startedAt);
        store.markCompleted(log.id);
        TriggerLog completed = store.findByTriggerIdAndEventName("trigger-1", "EventA").orElseThrow();
        assertEquals(TriggerLogStatus.COMPLETED, completed.status);
        assertNotNull(completed.finishedAt);
        TriggerLog otherEvent = store.record(log("trigger-1", "EventB"));
        store.markFailed(otherEvent.id, new IllegalStateException("failed"));
        TriggerLog failed = store.findByTriggerIdAndEventName("trigger-1", "EventB").orElseThrow();
        assertEquals(TriggerLogStatus.FAILED, failed.status);
        assertEquals("failed", failed.error);
    }

    @Test
    void triggerEventPairsDoNotCollideWhenTheirIdsContainSeparators() {
        store.record(log("a|b", "c"));
        store.record(log("a", "b|c"));
        assertNotEquals(store.findByTriggerIdAndEventName("a|b", "c").orElseThrow().id,
                store.findByTriggerIdAndEventName("a", "b|c").orElseThrow().id);
    }

    private TriggerLog log(String triggerId, String eventName) {
        TriggerLog log = new TriggerLog();
        log.triggerId = triggerId;
        log.eventName = eventName;
        log.triggerTime = Instant.parse("2026-09-30T00:00:00Z");
        log.status = TriggerLogStatus.RECEIVED;
        return log;
    }
}
