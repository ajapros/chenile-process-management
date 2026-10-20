package org.chenile.orchestrator.process.configuration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.chenile.orchestrator.process.SpringTestConfig;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.config.reader.ProcessConfigurator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = SpringTestConfig.class,
    properties = {
      "chenile.process.configurator=json",
      "chenile.process.management.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:definition-json-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureMockMvc
@ActiveProfiles("unittest")
class ProcessDefinitionJsonRestTest {
  @Autowired MockMvc mvc;
  @Autowired ProcessConfigurator configurator;

  @Test
  void jsonDefinitionsAreReadableButWritesAreRejectedThroughTheHighway() throws Exception {
    ProcessDef definition = new ProcessDef();
    definition.processType = "json-only";
    definition.leaf = true;
    configurator.processes.processMap.put(definition.processType, definition);
    try {
      mvc.perform(get("/process-definitions/json-only").header("x-chenile-tenant-id", "alpha"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.payload.processType").value("json-only"));
      mvc.perform(get("/process-definitions").header("x-chenile-tenant-id", "alpha"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.payload").isArray());
      mvc.perform(
              post("/process-definitions")
                  .header("x-chenile-tenant-id", "alpha")
                  .contentType("application/json")
                  .content("{\"processType\":\"new\"}"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.success").value(false));
      mvc.perform(
              put("/process-definitions/json-only")
                  .header("x-chenile-tenant-id", "alpha")
                  .contentType("application/json")
                  .content("{\"leaf\":false}"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.success").value(false));
      mvc.perform(get("/process-definitions/unknown").header("x-chenile-tenant-id", "alpha"))
          .andExpect(status().isNotFound());
    } finally {
      configurator.processes.processMap.remove(definition.processType);
    }
  }
}
