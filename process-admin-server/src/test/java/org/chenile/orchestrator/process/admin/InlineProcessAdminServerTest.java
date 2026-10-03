package org.chenile.orchestrator.process.admin;

import org.springframework.boot.test.context.SpringBootTest;

/** The runnable host also commits JDBC work when commands execute inline. */
@SpringBootTest(classes = ProcessAdminApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:inline-admin-server-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:chenile-process-work-schema.sql",
        "chenile.process.management.api-key=synthetic-server-test-administrator-key-123456",
        "chenile.process.outbox.enabled=false"})
class InlineProcessAdminServerTest extends ProcessAdminServerTest {}
