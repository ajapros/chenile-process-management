package org.chenile.orchestrator.process.definitionbdd;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(
    features = "src/test/resources/definition-features",
    glue = {"org.chenile.orchestrator.process.definitionbdd", "org.chenile.cucumber.rest"},
    plugin = "pretty")
public class CukesRestTest {}
