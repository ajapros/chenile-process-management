package org.chenile.orchestrator.process.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.spring.CucumberContextConfiguration;
import org.chenile.orchestrator.process.test.SpringConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
* This "steps" file's purpose is to hook up the SpringConfig to the test case.
* It does not contain any methods currently but can be used for writing your own custom BDD steps
* if required. In most cases people don't need additional steps since cucumber-utils provides for
* most of the steps. <br/>
* This class requires a dummy method to keep Cucumber from erring out. (Cucumber needs at least
* one step in a steps file)<br/>
*/
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,classes = SpringConfig.class)
@AutoConfigureMockMvc
@CucumberContextConfiguration
@ActiveProfiles("unittest")
public class CukesSteps {
    @org.springframework.beans.factory.annotation.Autowired
    org.chenile.orchestrator.process.outbox.OutboxDispatcher dispatcher;

    @io.cucumber.java.Before
    public void synchronousWorkers() { org.chenile.orchestrator.process.test.SharedData.SYNCH_MODE = true; }

    @io.cucumber.java.en.And("the process outbox is drained")
    public void drainOutbox() {
        dispatcher.drainAll(1000);
    }

	@Given("dummy") public void dummy(){}
}
