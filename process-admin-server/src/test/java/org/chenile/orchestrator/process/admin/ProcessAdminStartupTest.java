package org.chenile.orchestrator.process.admin;

import org.chenile.orchestrator.process.admin.configuration.ProcessAdminConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;

class ProcessAdminStartupTest {
    final ApplicationContextRunner application = new ApplicationContextRunner()
        .withUserConfiguration(ProcessAdminConfiguration.class)
        .withBean(org.springframework.jdbc.core.JdbcTemplate.class,
                () -> org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class))
        .withPropertyValues("chenile.process.management.enabled=true",
                "chenile.process.management.protect-all-http-routes=true",
                "spring.datasource.url=jdbc:postgresql://localhost/process");
    @Test void missingAndShortKeysFailBeforeStartup() {
        for (String key : new String[]{"", "short"}) application
            .withPropertyValues("chenile.process.management.api-key=" + key).run(context -> {
                assertNotNull(context.getStartupFailure());
                assertTrue(context.getStartupFailure().getMessage().contains("PROCESS_MANAGEMENT_API_KEY"));
            });
    }
    @Test void cannotDisableRouteProtectionOrSilentlyFallBackToAnEmbeddedDatabase() {
        application.withPropertyValues("chenile.process.management.api-key=synthetic-startup-test-key-123456789",
                "chenile.process.management.protect-all-http-routes=false").run(context -> {
            assertNotNull(context.getStartupFailure());
            assertTrue(context.getStartupFailure().getMessage().contains("protection of all HTTP routes"));
        });
        application.withPropertyValues("chenile.process.management.api-key=synthetic-startup-test-key-123456789",
                "spring.datasource.url=").run(context -> {
            assertNotNull(context.getStartupFailure());
            assertTrue(context.getStartupFailure().getMessage().contains("PROCESS_DATABASE_URL"));
        });
    }
    @Test void developmentExplicitlyAllowsH2WithAConfiguredKey() {
        application.withPropertyValues("chenile.process.management.api-key=synthetic-startup-test-key-123456789",
                "spring.datasource.url=jdbc:h2:mem:startup-test")
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
            .run(context -> assertNull(context.getStartupFailure()));
    }
}
