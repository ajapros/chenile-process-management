package org.chenile.orchestrator.process.configuration.management;

import com.fasterxml.jackson.databind.*;
import org.chenile.core.context.*;
import org.chenile.orchestrator.process.api.ProcessManager;
import org.chenile.orchestrator.process.config.reader.ProcessConfigurator;
import org.chenile.orchestrator.process.configuration.controller.ProcessManagementController;
import org.chenile.orchestrator.process.configuration.dao.*;
import org.chenile.orchestrator.process.model.*;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.*;
import org.chenile.orchestrator.process.outbox.OutboxDispatcher;
import org.chenile.orchestrator.process.WorkerStarter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import org.chenile.trigger.cron.CrontabRepository;
import org.chenile.trigger.store.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shared fixture and contracts whose behavior depends on command transport or database. */
@ActiveProfiles("unittest")
abstract class AbstractProcessManagementApiIntegrationTest {
    static final String BASE = "/process-management/api";
    static final String KEY = "integration-test-administrator-key-123456";
    @Autowired ProcessManagementController controller;
    @Autowired WebApplicationContext application;
    @Autowired FilterRegistrationBean<ManagementApiFilter> filter;
    @Autowired ProcessRepository processes;
    @Autowired ProcessDefinitionRepository definitions;
    @Autowired ProcessConfigurator configurator;
    @Autowired TriggerLogRepository executions;
    @Autowired CrontabRepository crontabs;
    @Autowired ProcessManager manager;
    @MockitoBean WorkerStarter workers;
    @Autowired ObjectProvider<OutboxDispatcher> dispatchers;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    final ObjectMapper json = new ObjectMapper();
    MockMvc mvc;

    @BeforeEach void setUp() {
        ContextContainer.CONTEXT_CONTAINER.clear();
        mvc = MockMvcBuilders.webAppContextSetup(application).addFilters(filter.getFilter()).build();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            executions.deleteAll(); crontabs.deleteAll();
            processes.deleteAll(); definitions.deleteAll();
            if (dispatchers.getIfAvailable() != null) {
                jdbc.update("delete from chenile_process_outbox"); jdbc.update("delete from chenile_process_receipt");
            }
        });
        ((org.chenile.orchestrator.process.service.defs.DatabaseProcessConfigurator) configurator).clearCache();
    }
    @AfterEach void clearContext() { ContextContainer.CONTEXT_CONTAINER.clear(); }
    MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String tenant) {
        return request.header("X-Process-Management-Key", KEY).header(HeaderUtils.TENANT_ID_KEY, tenant)
                .contentType("application/json");
    }
    JsonNode response(MockHttpServletRequestBuilder request, String tenant, int expected) throws Exception {
        var result = mvc.perform(authorized(request, tenant)).andExpect(status().is(expected)).andReturn();
        String body = result.getResponse().getContentAsString();
        return body.isEmpty() ? json.nullNode() : json.readTree(body);
    }
    void definition(String name, boolean leaf, String predecessor) throws Exception {
        response(post(BASE + "/definitions").content(json.writeValueAsString(Map.of("processType", name, "leaf", leaf,
                "predecessorProcessType", predecessor, "predecessorArgs", "BOTH"))), "alpha", 201);
    }
    JsonNode generate(String tenant, String key, String type) throws Exception {
        return response(post(BASE + "/triggers").content(json.writeValueAsString(Map.of("triggerId", key,
                "payload", Map.of("processDefName", type, "args", Map.of("rows", 2))))), tenant, 200);
    }
    void complete(String id, String tenant) {
        var context = ContextContainer.CONTEXT_CONTAINER; var original = context.snapshot();
        try {
            context.put(HeaderUtils.TENANT_ID_KEY, tenant);
            DoneSuccessfullyPayload done = new DoneSuccessfullyPayload(); done.output = "{\"result\":4}";
            manager.processById(id, Constants.Events.DONE_SUCCESSFULLY, done);
            if (dispatchers.getIfAvailable() != null) dispatchers.getObject().drainAll(30);
        } finally { context.restore(original); }
    }

    @Test void unifiedTriggerHistoryDeduplicatesAndNamespacesTenantKeys() throws Exception {
        definition("source", true, "");
        var first = generate("alpha", "same-key", "source");
        assertFalse(first.get("duplicate").asBoolean());
        var duplicate = generate("alpha", "same-key", "source");
        assertTrue(duplicate.get("duplicate").asBoolean());
        generate("beta", "same-key", "source");
        assertEquals(2, processes.count()); assertEquals(2, executions.count());
        var history = response(get(BASE + "/executions"), "alpha", 200);
        assertEquals(1, history.get("totalElements").asInt());
        var event = history.get("content").get(0);
        assertEquals("COMPLETED", event.get("status").asText());
        assertNotNull(event.get("startedAt")); assertNotNull(event.get("finishedAt"));
        assertEquals("ProcessCreate", event.get("eventName").asText());
        assertEquals("MANUAL", event.get("source").asText());
        assertEquals(1, response(get(BASE + "/executions").param("status", "COMPLETED"), "alpha", 200).get("totalElements").asInt());
        response(get(BASE + "/executions").param("status", "UNKNOWN"), "alpha", 400);
        var interrupted = new org.chenile.trigger.model.TriggerLog();
        interrupted.triggerId = "interrupted"; interrupted.eventName = "ProcessCreate";
        interrupted.triggerTime = java.time.Instant.now(); interrupted.tenant = "alpha";
        interrupted.status = org.chenile.trigger.model.TriggerLogStatus.RECEIVED;
        executions.saveAndFlush(interrupted);
        assertEquals(1, response(get(BASE + "/executions").param("status", "RECEIVED"), "alpha", 200).get("totalElements").asInt());
        assertEquals(1, response(get(BASE + "/trace").param("triggerId", interrupted.triggerId), "alpha", 200).get("nodes").size());
        var roots = response(get(BASE + "/processes").param("triggerId", event.get("triggerId").asText()), "alpha", 200);
        assertEquals(1, roots.get("totalElements").asInt());
        String processId = roots.get("content").get(0).get("id").asText();
        response(get(BASE + "/processes/" + processId), "beta", 404);
        response(get(BASE + "/trace").param("processId", processId), "beta", 404);
        response(get(BASE + "/trace").param("triggerId", event.get("triggerId").asText()), "beta", 404);
        response(get(BASE + "/processes").param("size", "101"), "alpha", 400);
        response(get(BASE + "/processes").param("page", "-1"), "alpha", 400);
        assertTrue(response(get(BASE + "/processes").param("page", "not-a-number"), "alpha", 400).has("message"));
    }
    @Test void linksTriggerToTerminalProcessAndSuccessorWithCompletionTimestamp() throws Exception {
        definition("source", true, ""); definition("successor", true, "source");
        var accepted = generate("alpha", "chain", "source");
        String triggerId = accepted.get("triggerId").asText();
        var root = processes.findAll().get(0); complete(root.id, "alpha");
        var successor = processes.findByPredecessorId(root.id).get(0);
        assertEquals("alpha", successor.tenant); assertEquals(triggerId, successor.triggerId);
        var detail = response(get(BASE + "/processes/" + root.id), "alpha", 200);
        assertEquals("PROCESSED", detail.get("process").get("status").asText());
        assertFalse(detail.has("completionEvents"));
        assertEquals(detail.get("process").get("stateEnteredAt"), detail.get("process").get("completedAt"));
        assertDoesNotThrow(() -> java.time.Instant.parse(detail.get("process").get("completedAt").asText()));
        var trace = response(get(BASE + "/trace").param("processId", successor.id), "alpha", 200);
        assertEquals(3, trace.get("nodes").size());
        Set<String> kinds = new HashSet<>(); trace.get("edges").forEach(e -> kinds.add(e.get("kind").asText()));
        assertEquals(Set.of("PROCESS_CREATE", "SUCCESSOR"), kinds);
        var filtered = response(get(BASE + "/processes").param("status", "EXECUTING").param("processType", "successor"), "alpha", 200);
        assertEquals(1, filtered.get("totalElements").asInt());
        assertTrue(filtered.get("content").get(0).get("completedAt").isNull());
        assertEquals(0, response(get(BASE + "/processes").param("status", "PROCESSED").param("processType", "successor"), "alpha", 200).get("totalElements").asInt());
    }
}
