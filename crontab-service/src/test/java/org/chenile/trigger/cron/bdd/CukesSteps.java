package org.chenile.trigger.cron.bdd;

import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.spring.CucumberContextConfiguration;
import org.chenile.core.context.ContextContainer;
import org.chenile.cucumber.VariableHelper;
import org.chenile.trigger.TriggerService;
import org.chenile.trigger.cron.CrontabRepository;
import org.quartz.Scheduler;
import org.quartz.JobKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import static org.mockito.Mockito.*;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = CrontabTestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("unittest")
public class CukesSteps {
    @Autowired CrontabRepository repository;
    @Autowired Scheduler scheduler;
    @Autowired TriggerService triggers;

    @Before public void resetData() {
        ContextContainer.getInstance().clear(); repository.deleteAll();
        clearInvocations(scheduler, triggers);
    }
    @Then("Quartz has scheduled the saved crontab")
    public void scheduled() throws Exception {
        String id = VariableHelper.substituteVariables("${id}");
        verify(scheduler).scheduleJob(argThat(job -> job.getKey().equals(new JobKey(id, "chenile-trigger"))), any());
    }
    @Then("Quartz has removed the saved crontab schedule")
    public void removed() throws Exception {
        verify(scheduler, atLeastOnce()).deleteJob(new JobKey(VariableHelper.substituteVariables("${id}"), "chenile-trigger"));
    }
    @Then("no trigger has been invoked")
    public void noTrigger() { verifyNoInteractions(triggers); }
}
