package org.chenile.orchestrator.process.bdd1;

import org.junit.runner.RunWith;
import org.springframework.test.context.ActiveProfiles;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;


@RunWith(Cucumber.class)

@CucumberOptions(features = "src/test/resources/features1",
    glue = {"classpath:org/chenile/orchestrator/process/bdd1",
    "classpath:org/chenile/cucumber/workflow",
    "classpath:org/chenile/cucumber/rest"},

        plugin = {"pretty"}
        )
@ActiveProfiles("unittest")
public class CukesRestTest1 {

}
