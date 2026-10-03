package org.chenile.orchestrator.process.admin.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ProcessAdminConfiguration {
    /** Fail before connecting to a database or starting Quartz/HTTP, without logging secrets. */
    @Bean static BeanFactoryPostProcessor processAdminStartupRequirements(Environment environment) {
        return factory -> {
            String key = environment.getProperty("chenile.process.management.api-key", "");
            if (key.length() < 32) throw new IllegalStateException("Set PROCESS_MANAGEMENT_API_KEY to a secret containing at least 32 characters");
            if (!environment.getProperty("chenile.process.management.enabled", Boolean.class, false)
                    || !environment.getProperty("chenile.process.management.protect-all-http-routes", Boolean.class, false))
                throw new IllegalStateException("The administration server requires the management API and protection of all HTTP routes");
            if (!environment.acceptsProfiles(Profiles.of("dev"))) {
                String url = environment.getProperty("spring.datasource.url", "");
                if (!url.startsWith("jdbc:postgresql:"))
                    throw new IllegalStateException("Set PROCESS_DATABASE_URL to a PostgreSQL JDBC URL, or use --spring.profiles.active=dev for local H2");
            }
        };
    }
    /** JDBC worker snapshots use Jackson 2; Boot's HTTP JSON support may use Jackson 3. */
    @Bean @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper processAdminObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
    @Bean ApplicationRunner verifyDeliverySchema(JdbcTemplate jdbc, Environment environment) {
        return args -> {
            String[] tables = environment.getProperty("chenile.process.outbox.enabled", Boolean.class, true)
                ? new String[]{"chenile_process_outbox", "chenile_process_receipt", "chenile_process_work_item"}
                : new String[]{"chenile_process_work_item"};
            for (String table : tables) {
                try { jdbc.queryForList("select 1 from " + table + " where 1=0"); }
                catch (org.springframework.dao.DataAccessException failure) {
                    throw new IllegalStateException("Required delivery table " + table + " is unavailable; apply the outbox/worker schema before starting", failure);
                }
            }
        };
    }
}
