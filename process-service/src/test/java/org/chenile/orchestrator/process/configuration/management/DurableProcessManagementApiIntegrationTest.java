package org.chenile.orchestrator.process.configuration.management;

import org.springframework.boot.test.context.SpringBootTest;
import org.chenile.orchestrator.process.SpringTestConfig;

/** The same HTTP contracts and execution history must work with either command transport. */
@SpringBootTest(classes = SpringTestConfig.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:durable-management-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "chenile.process.management.enabled=true", "chenile.process.configurator=database",
    "chenile.process.management.api-key=integration-test-administrator-key-123456",
    "chenile.process.outbox.enabled=true", "chenile.process.outbox.run-dispatcher=false",
    "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:chenile-process-outbox-schema.sql"})
class DurableProcessManagementApiIntegrationTest extends ProcessManagementApiIntegrationTest {}
