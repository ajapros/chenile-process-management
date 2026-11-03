package org.chenile.orchestrator.process.tenantbdd;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;
import org.springframework.test.context.ActiveProfiles;

@RunWith(Cucumber.class)
@CucumberOptions(
    features = "src/test/resources/tenant-features",
    glue = {
      "classpath:org/chenile/orchestrator/process/tenantbdd",
      "classpath:org/chenile/cucumber/rest"
    },
    plugin = {"pretty"})
@ActiveProfiles("unittest")
public class CukesRestTest {}
