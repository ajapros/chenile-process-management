package org.chenile.orchestrator.process.admin;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import org.chenile.orchestrator.process.outbox.OutboxDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/** Exercises the real runnable application's HTTP server, not a test-only host configuration. */
@SpringBootTest(
    classes = ProcessAdminApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:admin-server-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.sql.init.mode=always",
      "spring.sql.init.schema-locations=classpath:chenile-process-outbox-schema.sql",
      "chenile.process.management.api-key=synthetic-server-test-administrator-key-123456",
      "chenile.process.outbox.run-dispatcher=false"
    })
@ActiveProfiles("dev")
class ProcessAdminServerTest {
  private static final String KEY = "synthetic-server-test-administrator-key-123456";
  @LocalServerPort int port;
  @Autowired ObjectMapper json;
  @Autowired OutboxDispatcher dispatcher;
  private final HttpClient client = HttpClient.newHttpClient();

  private HttpResponse<String> request(
      String method, String path, String body, String key, String tenant) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json");
    if (key != null) request.header("X-Process-Management-Key", key);
    if (tenant != null) request.header("x-chenile-tenant-id", tenant);
    return client.send(
        request
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode ok(String method, String path, String body, int status) throws Exception {
    var response = request(method, path, body, KEY, "alpha");
    assertEquals(status, response.statusCode(), response.body());
    return json.readTree(response.body());
  }

  private void dispatch() {
    dispatcher.drainAll(20);
  }

  @Test
  void startsWithDatabaseDefinitionsQuartzAndProtectedWorkflowRoutes() throws Exception {
    String api = "/process-management/api";
    var info = ok("GET", api + "/info", null, 200);
    assertEquals("database", info.get("definitionSource").asText());
    assertTrue(info.get("definitionsWritable").asBoolean());
    assertTrue(info.get("cronAvailable").asBoolean());
    for (String route :
        new String[] {
          api + "/info",
          api + "/definitions",
          "/info",
          "/process/anything",
          "/processChildren/anything"
        }) assertEquals(401, request("GET", route, null, null, "alpha").statusCode(), route);
    assertEquals(401, request("POST", "/process", "{}", null, "alpha").statusCode());
    assertEquals(
        401,
        request("PATCH", "/process/anything/doneSuccessfully", "{}", null, "alpha").statusCode());
    assertEquals(401, request("GET", api + "/info", null, "wrong", "alpha").statusCode());
    assertEquals(400, request("GET", api + "/info", null, KEY, null).statusCode());
  }

  @Test
  void createsAndChainsProcessesThroughRealHttp() throws Exception {
    String api = "/process-management/api", suffix = UUID.randomUUID().toString();
    String source = "source-" + suffix, successor = "index-" + suffix;
    ok("POST", api + "/definitions", "{\"processType\":\"" + source + "\",\"leaf\":true}", 201);
    ok(
        "POST",
        api + "/definitions",
        "{\"processType\":\""
            + successor
            + "\",\"leaf\":true,\"predecessorProcessType\":\""
            + source
            + "\"}",
        201);
    String trigger =
        "{\"idempotencyKey\":\""
            + suffix
            + "\",\"payload\":{\"processDefName\":\""
            + source
            + "\",\"args\":{\"rows\":2}}}";
    var accepted = ok("POST", api + "/triggers", trigger, 202);
    assertEquals(accepted, ok("POST", api + "/triggers", trigger, 202));
    dispatch();
    var roots = ok("GET", api + "/processes?processType=" + source, null, 200);
    assertEquals(1, roots.get("totalElements").asInt());
    String id = roots.get("content").get(0).get("id").asText();
    dispatch();
    // In-VM delivery runs no business worker in this stock host, so the leaf process waits in
    // EXECUTING; drive its completion over HTTP exactly as a real worker callback would.
    ok("PATCH", "/process/" + id + "/doneSuccessfully", "{\"output\":\"finished\"}", 200);
    dispatch();
    var detail = ok("GET", api + "/processes/" + id, null, 200);
    assertEquals("PROCESSED", detail.get("process").get("status").asText());
    assertFalse(detail.has("completionEvents"));
    assertEquals(
        detail.get("process").get("stateEnteredAt"), detail.get("process").get("completedAt"));
    var next = ok("GET", api + "/processes?predecessorId=" + id, null, 200);
    assertEquals(1, next.get("totalElements").asInt());
    var trace = ok("GET", api + "/trace?processId=" + id, null, 200);
    assertEquals(3, trace.get("nodes").size());
    assertEquals(404, request("GET", api + "/processes/" + id, null, KEY, "beta").statusCode());
  }
}
