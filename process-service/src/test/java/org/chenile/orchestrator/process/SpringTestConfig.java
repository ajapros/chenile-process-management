package org.chenile.orchestrator.process;


import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;


@Configuration
@PropertySource("classpath:org/chenile/orchestrator/process/TestService.properties")
@SpringBootApplication(scanBasePackages = { "org.chenile.configuration", "org.chenile.orchestrator.**.configuration" })
@ActiveProfiles("unittest")
@EntityScan({"org.chenile.orchestrator.**.model", "org.chenile.trigger.model", "org.chenile.trigger.cron"})
@EnableJpaRepositories(basePackages = {"org.chenile.orchestrator.**.configuration.dao",
        "org.chenile.trigger.store", "org.chenile.trigger.cron"})
public class SpringTestConfig extends SpringBootServletInitializer{
    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Primary
    WorkerStarter testWorkerStarter() { return worker -> { }; }

	
}

