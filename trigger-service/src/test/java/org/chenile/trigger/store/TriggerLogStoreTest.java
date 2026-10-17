package org.chenile.trigger.store;

import org.chenile.trigger.model.TriggerLog;
import org.chenile.trigger.model.TriggerLogStatus;
import org.chenile.core.context.ContextContainer;
import org.chenile.core.context.HeaderUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
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
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:chenile-trigger-schema.sql"})
class TriggerLogStoreTest {
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = TriggerLog.class)
    @EnableJpaRepositories(basePackageClasses = TriggerLogRepository.class)
    @Import(DatabaseTriggerLogStore.class)
    static class Config { }

    @Autowired TriggerLogStore store;
    private final ContextContainer context = ContextContainer.getInstance();
    private ContextContainer.ContextSnapshot previousContext;

    @BeforeEach void setUpContext() {
        previousContext = context.snapshot();
        context.clear();
        context.setTenant("caller-tenant");
        context.put(HeaderUtils.AUTH_USER_KEY, "trigger-user");
    }

    @AfterEach void restoreContext() { context.restore(previousContext); }

    @Test
    void databaseRejectsDuplicateTriggerEventPairsAndPersistsTheirOutcome() {
        TriggerLog input = log("trigger-1", "EventA");
        input.tenant = "trigger-tenant";
        input.triggerContext = "{\"source\":\"cron\"}";
        TriggerLog log = store.record(input);
        assertNotNull(log.id);
        assertNotNull(log.getCreatedTime());
        assertNotNull(log.getLastModifiedTime());
        assertEquals("trigger-user", log.getCreatedBy());
        assertEquals("trigger-user", log.getLastModifiedBy());
        assertEquals("trigger-tenant", log.tenant);
        assertEquals("caller-tenant", context.getTenant());
        long initialVersion = log.version;
        assertThrows(DataIntegrityViolationException.class,
                () -> store.record(log("trigger-1", "EventA")));
        assertEquals("caller-tenant", context.getTenant());
        TriggerLog running = store.markRunning(log.id);
        assertNotNull(running.startedAt);
        assertEquals("trigger-tenant", running.tenant);
        assertEquals("caller-tenant", context.getTenant());
        store.markCompleted(log.id);
        TriggerLog completed = store.findByTriggerIdAndEventName("trigger-1", "EventA").orElseThrow();
        assertEquals(TriggerLogStatus.COMPLETED, completed.status);
        assertNotNull(completed.finishedAt);
        assertEquals("trigger-tenant", completed.tenant);
        assertEquals(log.getCreatedTime(), completed.getCreatedTime());
        assertTrue(completed.version > initialVersion);
        assertEquals(input.triggerContext, completed.triggerContext);
        assertEquals(input.triggerTime, completed.triggerTime);
        assertEquals("caller-tenant", context.getTenant());
        TriggerLog otherEvent = store.record(log("trigger-1", "EventB"));
        store.markFailed(otherEvent.id, new IllegalStateException("failed"));
        TriggerLog failed = store.findByTriggerIdAndEventName("trigger-1", "EventB").orElseThrow();
        assertEquals(TriggerLogStatus.FAILED, failed.status);
        assertEquals("failed", failed.error);
        assertNull(failed.tenant);
        assertEquals("caller-tenant", context.getTenant());
    }

    @Test
    void triggerEventPairsDoNotCollideWhenTheirIdsContainSeparators() {
        TriggerLog input = log("a|b", "c");
        input.triggerTime = null;
        assertNotNull(store.record(input).triggerTime);
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
