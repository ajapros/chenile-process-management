package org.chenile.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.core.context.ChenileExchange;
import org.chenile.core.context.ContextContainer;
import org.chenile.core.event.EventProcessor;
import org.chenile.orchestrator.process.outbox.*;
import org.chenile.trigger.model.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.chenile.base.exception.NotFoundException;
import org.chenile.base.exception.BadRequestException;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real durable acceptance and dispatch, including transaction rollback and concurrent submissions. */
class EventTriggerServiceTest {
    final ContextContainer context = ContextContainer.getInstance();
    ContextContainer.ContextSnapshot previous;
    JdbcTemplate jdbc;
    DataSourceTransactionManager manager;
    ProcessOutboxRepository repository;
    EventProcessor events;
    EventTriggerService service;
    OutboxDispatcher dispatcher;
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach void setUp() {
        previous = context.snapshot(); context.clear(); context.setTenant("alpha");
        var data = new JdbcDataSource();
        data.setURL("jdbc:h2:mem:trigger-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("chenile-process-outbox-schema.sql")).execute(data);
        jdbc = new JdbcTemplate(data);
        jdbc.execute("create table local_effect (trigger_id varchar primary key)");
        manager = new DataSourceTransactionManager(data);
        repository = new ProcessOutboxRepository(jdbc, manager);
        events = mock(EventProcessor.class);
        var command = new DispatchTriggerCommand(repository, events, mapper);
        service = new EventTriggerService(repository, command, mapper, manager);
        var policy = OutboxRetryPolicy.defaults(); policy.initialIntervalMillis = 0; policy.maxAttempts = 2;
        dispatcher = new OutboxDispatcher(repository, new OutboxCommandRegistry(List.of(command)), policy, "worker", 300);
    }
    @AfterEach void restore() { context.restore(previous); }

    TriggerInput input(String key) {
        var input = new TriggerInput(); input.idempotencyKey = key; input.eventId = "CustomerEvent";
        input.headers.put("x-chenile-tenant-id", "alpha"); input.headers.put("x-request-id", "request-1");
        input.headers.put("x-chenile-trigger-id", "spoofed");
        input.triggerContext.put("adapter", "test"); input.payload = "  {\"customerId\":7}  ";
        return input;
    }

    @Test void acceptanceReturnsOnlyTheDurableIdAndDispatchPropagatesThatIdentity() throws Exception {
        context.put("x-caller-correlation", "caller-1");
        var input = input("same-key");
        input.source = "CUSTOM";
        input.headers.put("x-chenile-trigger-source", "HEADER-MUST-NOT-WIN");
        var ack = service.trigger(input);
        assertEquals(ack, service.trigger(input));
        assertEquals("PENDING", service.status(ack.triggerId()).status());
        assertEquals("CUSTOM", service.status(ack.triggerId()).source());
        var persisted = mapper.readValue(repository.findById(ack.triggerId()).orElseThrow().payload,
                org.chenile.trigger.model.Trigger.class);
        assertEquals("CUSTOM", persisted.source());
        verifyNoInteractions(events);
        assertEquals(1, jdbc.queryForObject("select count(*) from chenile_process_outbox", Integer.class));
        assertNull(repository.findById(ack.triggerId()).orElseThrow().processId);
        assertEquals(Set.of("triggerId"), mapper.convertValue(ack, Map.class).keySet());
        when(events.handleEvent(anyString(), any(), anyMap())).thenAnswer(call -> {
            assertEquals(input.payload, call.getArgument(1));
            Map<String,String> headers = call.getArgument(2);
            assertEquals(ack.triggerId(), headers.get("x-chenile-trigger-id"));
            assertEquals("alpha", context.getTenant());
            assertEquals(ack.triggerId(), context.get("x-chenile-trigger-id"));
            assertEquals("request-1", context.get("x-request-id"));
            assertEquals("caller-1", headers.get("x-caller-correlation"));
            var exchange = new ChenileExchange(); exchange.setResponse("only logged"); return List.of(exchange);
        });
        context.setTenant("dispatcher-caller");
        assertTrue(dispatcher.processOne()); assertFalse(dispatcher.processOne());
        assertEquals("dispatcher-caller", context.getTenant());
        context.setTenant("alpha");
        var status = service.status(ack.triggerId());
        assertEquals("DONE", status.status()); assertEquals(1, status.attempt());
        assertNotNull(status.startedAt()); assertNotNull(status.finishedAt());
        assertEquals(input.triggerContext, status.triggerContext());
        assertEquals(input.payload, status.payload());
        assertEquals(ack, service.trigger(input));
    }

    @Test void failedDispatchRollsBackEffectsAndRetriesTheSameCommandOnceToCompletion() {
        var withoutSource = input("retry");
        withoutSource.source = null;
        withoutSource.headers.put("x-chenile-trigger-source", "IGNORED");
        var ack = service.trigger(withoutSource);
        assertEquals("EXTERNAL", service.status(ack.triggerId()).source());
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        when(events.handleEvent(anyString(), any(), anyMap())).thenAnswer(call -> {
            jdbc.update("insert into local_effect values (?)", ack.triggerId());
            if (calls.incrementAndGet() == 1) {
                var failed = new ChenileExchange(); failed.setException(new IllegalStateException("temporary"));
                return List.of(failed);
            }
            return List.of(new ChenileExchange());
        });
        assertTrue(dispatcher.processOne());
        assertEquals(0, jdbc.queryForObject("select count(*) from local_effect", Integer.class));
        assertEquals("PENDING", service.status(ack.triggerId()).status());
        assertTrue(dispatcher.processOne()); assertFalse(dispatcher.processOne());
        assertEquals(1, jdbc.queryForObject("select count(*) from local_effect", Integer.class));
        assertEquals("DONE", service.status(ack.triggerId()).status());
        assertEquals(2, service.status(ack.triggerId()).attempt());
        assertEquals(ack, service.trigger(input("retry")));
    }

    @Test void concurrentSubmissionReturnsOneAcknowledgementAndAcceptanceSurvivesOuterRollback() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            Callable<TriggerResult> submit = () -> { start.await(); return service.trigger(input("concurrent")); };
            var first = pool.submit(submit); var second = pool.submit(submit); start.countDown();
            assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("select count(*) from chenile_process_outbox", Integer.class));
        } finally { pool.shutdownNow(); }
        var outer = new TransactionTemplate(manager);
        var ack = outer.execute(tx -> { var accepted = service.trigger(input("outer")); tx.setRollbackOnly(); return accepted; });
        assertEquals("PENDING", service.status(ack.triggerId()).status());
        assertNotEquals(service.trigger(input(null)).triggerId(), service.trigger(input(null)).triggerId());
    }

    @Test void permanentFailuresRemainQueryableAndTenantsCannotReadOrDeduplicateEachOther() {
        var input = input("failed"); var ack = service.trigger(input);
        when(events.handleEvent(anyString(), any(), anyMap())).thenReturn(List.of());
        assertTrue(dispatcher.processOne()); assertTrue(dispatcher.processOne()); assertFalse(dispatcher.processOne());
        assertEquals("DEAD", service.status(ack.triggerId()).status());
        assertNotNull(service.status(ack.triggerId()).error());
        context.setTenant("beta");
        assertThrows(NotFoundException.class, () -> service.status(ack.triggerId()));
        input.headers.put("x-chenile-tenant-id", "beta");
        assertNotEquals(ack.triggerId(), service.trigger(input).triggerId());
        input.headers.put("invalid-header", "bad");
        assertThrows(BadRequestException.class, () -> service.trigger(input));
    }
}
