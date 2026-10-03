package org.chenile.orchestrator.process.configuration.management;

import com.fasterxml.jackson.databind.*;
import org.chenile.core.context.*;
import org.chenile.orchestrator.process.SpringTestConfig;
import org.chenile.orchestrator.process.api.ProcessManager;
import org.chenile.orchestrator.process.config.reader.ProcessConfigurator;
import org.chenile.orchestrator.process.configuration.controller.ProcessManagementController;
import org.chenile.orchestrator.process.configuration.dao.*;
import org.chenile.orchestrator.process.model.*;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.*;
import org.chenile.orchestrator.process.outbox.OutboxDispatcher;
import org.chenile.orchestrator.process.service.defs.PostSaveHook;
import org.chenile.trigger.cron.CrontabRepository;
import org.chenile.trigger.store.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

@SpringBootTest(classes = SpringTestConfig.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:management-api;DB_CLOSE_DELAY=-1",
    "chenile.process.management.enabled=true", "chenile.process.configurator=database",
    "chenile.process.management.api-key=integration-test-administrator-key-123456"})
@ActiveProfiles("unittest")
class ProcessManagementApiIntegrationTest {
    static final String BASE = "/process-management/api";
    static final String KEY = "integration-test-administrator-key-123456";
    @Autowired ProcessManagementController controller;
    @Autowired WebApplicationContext application;
    @Autowired FilterRegistrationBean<ManagementApiFilter> filter;
    @Autowired ProcessRepository processes;
    @Autowired CompletionEventRepository completions;
    @Autowired ProcessDefinitionRepository definitions;
    @Autowired ProcessConfigurator configurator;
    @Autowired TriggerExecutionRepository executions;
    @Autowired TriggerLogRepository claims;
    @Autowired CrontabRepository crontabs;
    @Autowired ProcessManager manager;
    @Autowired PostSaveHook workers;
    @Autowired ObjectProvider<OutboxDispatcher> dispatchers;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    final ObjectMapper json = new ObjectMapper();
    MockMvc mvc;

    @BeforeEach void setUp() {
        ContextContainer.CONTEXT_CONTAINER.clear();
        mvc = MockMvcBuilders.webAppContextSetup(application).addFilters(filter.getFilter()).build();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            completions.deleteAll(); executions.deleteAll(); claims.deleteAll(); crontabs.deleteAll();
            processes.deleteAll(); definitions.deleteAll();
            if (dispatchers.getIfAvailable() != null) {
                jdbc.update("delete from chenile_process_outbox"); jdbc.update("delete from chenile_process_receipt");
            }
        });
        ((org.chenile.orchestrator.process.service.defs.DatabaseProcessConfigurator) configurator).clearCache();
        workers.setWorkerStarter(worker -> {});
    }
    @AfterEach void clearContext() { ContextContainer.CONTEXT_CONTAINER.clear(); workers.setWorkerStarter(null); }
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

    @Test void protectsEveryReadAndWriteAndRestoresRequestContext() throws Exception {
        for (String route : List.of("/info", "/definitions", "/processes", "/trace", "/executions", "/crontabs"))
            mvc.perform(get(BASE + route)).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/definitions").content("{}").contentType("application/json"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/info").header("X-Process-Management-Key", KEY)).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/info").header("X-Process-Management-Key", KEY).header(HeaderUtils.TENANT_ID_KEY, "bad:tenant"))
                .andExpect(status().isBadRequest());
        ContextContainer.CONTEXT_CONTAINER.put(HeaderUtils.TENANT_ID_KEY, "original");
        response(get(BASE + "/info"), "alpha", 200);
        assertEquals("original", ContextContainer.CONTEXT_CONTAINER.get(HeaderUtils.TENANT_ID_KEY));
        assertThrows(IllegalArgumentException.class, () -> new ManagementApiFilter("short"));
    }
    @Test void definitionWritesInvalidateCachedMissesAndRejectCycles() throws Exception {
        assertNull(configurator.findByName("source"));
        assertTrue(configurator.findByPredecessorProcessType("source").isEmpty());
        definition("source", true, ""); definition("successor", true, "source");
        assertNotNull(configurator.findByName("source"));
        assertEquals(1, configurator.findByPredecessorProcessType("source").size());
        assertEquals(2, response(get(BASE + "/definitions"), "alpha", 200).size());
        response(post(BASE + "/definitions").content("{\"processType\":\"source\"}"), "alpha", 409);
        response(put(BASE + "/definitions/source").content("{\"processType\":\"source\",\"predecessorProcessType\":\"successor\"}"), "alpha", 400);
        response(put(BASE + "/definitions/source").content("{\"processType\":\"different\"}"), "alpha", 400);
        response(post(BASE + "/definitions").content("{\"processType\":\"invalid\",\"predecessorArgs\":\"NONE\"}"), "alpha", 400);
        response(put(BASE + "/definitions/source").content("{\"processType\":\"source\",\"leaf\":true,\"config\":{\"chunkSize\":\"20\"}}"), "alpha", 200);
        assertEquals("20", configurator.findByName("source").config.get("chunkSize"));
    }
    @Test void triggerHistoryIsSeparateFromIdempotencyAndNamespacesTenantRetries() throws Exception {
        definition("source", true, "");
        var first = generate("alpha", "same-key", "source");
        assertFalse(first.get("duplicate").asBoolean());
        var duplicate = generate("alpha", "same-key", "source");
        assertTrue(duplicate.get("duplicate").asBoolean());
        generate("beta", "same-key", "source");
        assertEquals(2, processes.count()); assertEquals(2, claims.count()); assertEquals(2, executions.count());
        var history = response(get(BASE + "/executions"), "alpha", 200);
        assertEquals(1, history.get("totalElements").asInt());
        var event = history.get("content").get(0);
        assertEquals("COMPLETED", event.get("status").asText());
        assertNotNull(event.get("startedAt")); assertNotNull(event.get("finishedAt"));
        assertEquals("ProcessCreate", event.get("eventName").asText());
        assertEquals("MANUAL", event.get("source").asText());
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
    @Test void linksTriggerToCompletionAndSuccessorWithGenerationTimestamp() throws Exception {
        definition("source", true, ""); definition("successor", true, "source");
        var accepted = generate("alpha", "chain", "source");
        String triggerId = accepted.get("triggerId").asText();
        var root = processes.findAll().get(0); complete(root.id, "alpha");
        var successor = processes.findByPredecessorId(root.id).get(0);
        assertEquals("alpha", successor.tenant); assertEquals(triggerId, successor.triggerId);
        var detail = response(get(BASE + "/processes/" + root.id), "alpha", 200);
        assertEquals("PROCESSED", detail.get("process").get("status").asText());
        var event = detail.get("completionEvents").get(0);
        assertDoesNotThrow(() -> java.time.Instant.parse(event.get("generatedAt").asText()));
        assertEquals(root.id, json.readTree(event.get("payload").asText()).get("processId").asText());
        var trace = response(get(BASE + "/trace").param("processId", successor.id), "alpha", 200);
        assertEquals(4, trace.get("nodes").size());
        Set<String> kinds = new HashSet<>(); trace.get("edges").forEach(e -> kinds.add(e.get("kind").asText()));
        assertEquals(Set.of("PROCESS_CREATE", "EMITS", "SUCCESSOR"), kinds);
        var filtered = response(get(BASE + "/processes").param("status", "EXECUTING").param("processType", "successor"), "alpha", 200);
        assertEquals(1, filtered.get("totalElements").asInt());
        assertEquals(0, response(get(BASE + "/processes").param("status", "PROCESSED").param("processType", "successor"), "alpha", 200).get("totalElements").asInt());
    }
    @Test void subprocessQueriesAndLegacyTraceRemainTenantScoped() throws Exception {
        Process parent = new Process("parent", false); parent.id = "legacy-parent";
        Process child = new Process("child", true); child.id = "legacy-child"; child.parentId = parent.id;
        Process foreign = new Process("foreign", true); foreign.id = "foreign-child"; foreign.parentId = parent.id;
        var context = ContextContainer.CONTEXT_CONTAINER;
        context.put(HeaderUtils.TENANT_ID_KEY, "alpha"); processes.saveAllAndFlush(List.of(parent, child));
        context.put(HeaderUtils.TENANT_ID_KEY, "beta"); processes.saveAndFlush(foreign); context.clear();
        assertEquals(1, response(get(BASE + "/processes").param("parentId", parent.id), "alpha", 200).get("totalElements").asInt());
        var trace = response(get(BASE + "/trace").param("processId", child.id), "alpha", 200);
        assertEquals(2, trace.get("nodes").size()); assertEquals("SUBPROCESS", trace.get("edges").get(0).get("kind").asText());
        assertFalse(trace.toString().contains("foreign-child"));
        response(get(BASE + "/trace"), "alpha", 400);
        response(get(BASE + "/trace").param("processId", child.id).param("triggerId", "x"), "alpha", 400);
    }
    @Test void addsValidatesAndDisablesCronSchedulesWithoutCrossTenantWrites() throws Exception {
        definition("source", true, "");
        String body = """
          {"name":"daily-source","cronExpression":"0 0 0 1 1 ? 2099","timezone":"UTC","enabled":true,
           "payload":{"processDefName":"source","args":{"batch":true}}}
          """;
        var saved = response(post(BASE + "/crontabs").content(body), "alpha", 201);
        String id = saved.get("id").asText();
        assertEquals("alpha", saved.get("tenant").asText());
        assertEquals("ProcessCreate", saved.get("eventName").asText());
        assertEquals(0, response(get(BASE + "/crontabs"), "beta", 200).get("totalElements").asInt());
        response(put(BASE + "/crontabs/" + id).content(body), "beta", 404);
        response(put(BASE + "/crontabs/" + id).content(body.replace("\"enabled\":true", "\"enabled\":false")), "alpha", 200);
        assertFalse(crontabs.findById(id).orElseThrow().enabled);
        response(post(BASE + "/crontabs").content(body.replace("0 0 0 1 1 ? 2099", "invalid")), "alpha", 400);
        response(post(BASE + "/crontabs").content(body.replace("UTC", "Mars/Base")), "alpha", 400);
        assertEquals(1, crontabs.count());
    }
    @Test void refusesOversizedTracesInsteadOfSilentlyOmittingProcesses() throws Exception {
        List<Process> rows = new ArrayList<>();
        for (int i = 0; i < 1001; i++) { var p = new Process("large", true); p.triggerId = "large-trigger"; rows.add(p); }
        ContextContainer.CONTEXT_CONTAINER.put(HeaderUtils.TENANT_ID_KEY, "alpha"); processes.saveAllAndFlush(rows);
        ContextContainer.CONTEXT_CONTAINER.clear();
        response(get(BASE + "/trace").param("triggerId", "large-trigger"), "alpha", 413);
    }
}
