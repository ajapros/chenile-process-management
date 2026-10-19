package org.chenile.trigger.cron.bdd;

import org.chenile.trigger.TriggerService;
import org.chenile.trigger.cron.Crontab;
import org.chenile.trigger.cron.CrontabRepository;
import org.quartz.Scheduler;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import static org.mockito.Mockito.*;

/** Host application owns persistence discovery; library configuration never scans entities or repositories. */
@SpringBootApplication(scanBasePackages = {"org.chenile.configuration.core", "org.chenile.configuration.http",
        "org.chenile.configuration.crontab"})
@EntityScan(basePackageClasses = Crontab.class)
@EnableJpaRepositories(basePackageClasses = CrontabRepository.class)
public class CrontabTestApplication {
    @Bean public TriggerService triggerService() { return mock(TriggerService.class); }
    @Bean public Scheduler quartzScheduler() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        when(scheduler.isStarted()).thenReturn(true);
        return scheduler;
    }
}
