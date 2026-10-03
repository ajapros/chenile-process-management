package org.chenile.orchestrator.process.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Hosts the existing framework controllers, command transports, scheduler and management API. */
@SpringBootApplication(scanBasePackages = {
    // Only the runtime modules this host needs: do not instantiate remote proxy/registry clients.
    "org.chenile.configuration.core",
    "org.chenile.configuration.http",
    "org.chenile.configuration.utils",
    "org.chenile.configuration.workflow",
    "org.chenile.configuration.scheduler",
    "org.chenile.configuration.process",
    "org.chenile.orchestrator.process.configuration",
    "org.chenile.orchestrator.process.admin.configuration"})
@EntityScan(basePackages = {
    "org.chenile.orchestrator.process.model",
    "org.chenile.orchestrator.process.configuration.model"})
@EnableJpaRepositories(basePackages = "org.chenile.orchestrator.process.configuration.dao")
public class ProcessAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProcessAdminApplication.class, args);
    }
}
