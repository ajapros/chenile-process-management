package org.chenile.orchestrator.process.configuration.management;

import org.chenile.core.context.ContextContainer;
import org.chenile.core.context.HeaderUtils;
import org.chenile.orchestrator.process.SpringTestConfig;
import org.chenile.orchestrator.process.model.Process;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SpringTestConfig.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:management-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "chenile.process.management.enabled=true", "chenile.process.configurator=database",
    "chenile.process.management.api-key=integration-test-administrator-key-123456"})
/** HTTP validation and query contracts run once; transport contracts are inherited. */
class ProcessManagementApiIntegrationTest extends AbstractProcessManagementApiIntegrationTest {
    @Test void triggerEndpointUsesChenileDispatchAndRetainsDuplicateIdentity() throws Exception {
        definition("source", true, "");
        String body = """
                {"idempotencyKey":"http-trigger", "eventId":"ProcessCreate",
                 "triggerTime":"2026-10-16T00:00:00Z",
                 "source":"HTTP", "headers":{"x-chenile-tenant-id":"alpha"},
                 "payload":{"processDefName":"source", "triggerId":"spoofed", "args":{"rows":2}}}
                """;
        var first = response(post("/trigger").content(body), "alpha", 202).get("payload");
        assertNotNull(first);
        assertNotEquals("http-trigger", first.get("triggerId").asText());
        var duplicate = response(post("/trigger").content(body), "alpha", 202).get("payload");
        assertEquals(first, duplicate);
        assertEquals(0, processes.count());
        String id = first.get("triggerId").asText();
        var pending = response(get("/trigger/" + id), "alpha", 200).get("payload");
        assertEquals("PENDING", pending.get("status").asText());
        assertEquals("HTTP", pending.get("source").asText());
        response(get("/trigger/" + id), "beta", 404);
        org.mockito.Mockito.verifyNoInteractions(workers);
        dispatcher.drainAll(30);
        assertEquals(1, processes.count());
        var process = processes.findAll().getFirst();
        assertEquals(id, process.triggerId);
        assertEquals("alpha", process.tenant);
        var status = response(get("/trigger/" + id), "alpha", 200).get("payload");
        assertEquals("HTTP", status.get("source").asText());
        assertEquals("DONE", status.get("status").asText());
        verify(workers).start(any());
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
