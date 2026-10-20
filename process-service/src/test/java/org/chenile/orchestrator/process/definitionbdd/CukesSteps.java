package org.chenile.orchestrator.process.definitionbdd;

import static org.junit.jupiter.api.Assertions.*;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import org.chenile.core.context.ContextContainer;
import org.chenile.orchestrator.process.SpringTestConfig;
import org.chenile.orchestrator.process.api.ProcessDefinitionService;
import org.chenile.orchestrator.process.config.reader.ProcessConfigurator;
import org.chenile.orchestrator.process.configuration.dao.ProcessDefinitionRepository;
import org.chenile.orchestrator.process.service.defs.DatabaseProcessConfigurator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@CucumberContextConfiguration
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = SpringTestConfig.class,
    properties = {
      "chenile.process.configurator=database",
      "chenile.process.management.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:definition-bdd;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureMockMvc
@ActiveProfiles("unittest")
public class CukesSteps {
  @Autowired ProcessDefinitionRepository repository;
  @Autowired ProcessDefinitionService service;
  @Autowired ProcessConfigurator configurator;
  @Autowired PlatformTransactionManager transactions;

  @Before
  public void resetDefinitions() {
    ContextContainer.getInstance().clear();
    repository.deleteAll();
    ((DatabaseProcessConfigurator) configurator).clearCache();
  }

  @Given("the runtime definition {string} is absent")
  public void missing(String name) {
    assertNull(configurator.findByName(name));
  }

  @Then("the runtime definition {string} has config {string} equal to {string}")
  public void runtimeConfig(String name, String key, String value) {
    assertEquals(value, configurator.findByName(name).config.get(key));
  }

  @When("an update to definition {string} with config {string} equal to {string} is rolled back")
  public void rollback(String name, String key, String value) {
    var definition = service.retrieve(name);
    definition.config.put(key, value);
    assertThrows(
        IllegalStateException.class,
        () ->
            new TransactionTemplate(transactions)
                .executeWithoutResult(
                    tx -> {
                      service.update(name, definition);
                      throw new IllegalStateException("Intentional rollback");
                    }));
  }
}
