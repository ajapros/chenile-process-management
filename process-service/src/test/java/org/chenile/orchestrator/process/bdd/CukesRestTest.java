package org.chenile.orchestrator.process.bdd;

import org.junit.runner.RunWith;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.ActiveProfiles;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;


@RunWith(Cucumber.class)

@CucumberOptions(features = "src/test/resources/features",
    glue = {"classpath:org/chenile/orchestrator/process/bdd",
    "classpath:org/chenile/cucumber/workflow",
    "classpath:org/chenile/cucumber/rest"},

        plugin = {"pretty"}
        )
@ActiveProfiles("unittest")
@EnableAspectJAutoProxy
public class CukesRestTest {

}
