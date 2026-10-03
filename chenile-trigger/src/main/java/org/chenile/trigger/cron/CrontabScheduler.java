package org.chenile.trigger.cron;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.trigger.TriggerService;
import org.chenile.trigger.model.TriggerInput;
import org.quartz.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

public class CrontabScheduler {
    private final CrontabRepository repository;
    private final TriggerService triggerService;
    private final Scheduler scheduler;
    private final ObjectMapper mapper = new ObjectMapper();

    public CrontabScheduler(CrontabRepository repository, TriggerService triggerService, Scheduler scheduler) {
        this.repository = repository;
        this.triggerService = triggerService;
        this.scheduler = scheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void schedulePersistedCrontabs() throws SchedulerException {
        if (!scheduler.isStarted()) scheduler.start();
        for (Crontab crontab : repository.findByEnabledTrue()) schedule(crontab);
    }

    public void schedule(Crontab crontab) throws SchedulerException {
        validate(crontab);
        JobKey jobKey = JobKey.jobKey(crontab.id, "chenile-trigger");
        TriggerKey triggerKey = TriggerKey.triggerKey(crontab.id, "chenile-trigger");
        scheduler.deleteJob(jobKey);
        if (!crontab.enabled) return;
        JobDataMap data = new JobDataMap();
        data.put(QuartzTriggerJob.SCHEDULER, this);
        data.put(QuartzTriggerJob.CRONTAB_ID, crontab.id);
        JobDetail job = newJob(QuartzTriggerJob.class).withIdentity(jobKey).usingJobData(data).build();
        CronScheduleBuilder schedule = CronScheduleBuilder.cronSchedule(crontab.cronExpression)
                .inTimeZone(java.util.TimeZone.getTimeZone(ZoneId.of(crontab.timezone)));
        scheduler.scheduleJob(job, newTrigger().withIdentity(triggerKey).forJob(job).withSchedule(schedule).build());
    }

    public void unschedule(String id) throws SchedulerException { scheduler.deleteJob(JobKey.jobKey(id, "chenile-trigger")); }

    public void fire(String id, JobExecutionContext context) {
        Crontab crontab = repository.findById(id).orElse(null);
        if (crontab == null || !crontab.enabled) return;
        TriggerInput input = new TriggerInput();
        input.triggerId = "crontab:" + crontab.id + ":" + context.getFireInstanceId();
        input.triggerTime = context.getFireTime().toInstant();
        input.eventId = crontab.eventName;
        input.headers.putAll(map(crontab.headersJson));
        if (crontab.tenant != null) input.headers.put("x-chenile-tenant-id", crontab.tenant);
        input.headers.put("chenile-trigger-source", "CRON");
        input.headers.put("chenile-crontab-id", crontab.id);
        input.headers.put("chenile-quartz-fire-instance-id", context.getFireInstanceId());
        input.headers.put("chenile-quartz-scheduled-fire-time", String.valueOf(context.getScheduledFireTime().toInstant()));
        input.headers.put("chenile-quartz-fire-time", String.valueOf(context.getFireTime().toInstant()));
        if (context.getNextFireTime() != null)
            input.headers.put("chenile-quartz-next-fire-time", String.valueOf(context.getNextFireTime().toInstant()));
        input.payload = object(crontab.eventPayloadJson);
        triggerService.trigger(input);
    }

    void validate(Crontab crontab) {
        if (crontab.name == null || crontab.name.isBlank() || crontab.cronExpression == null || crontab.eventName == null)
            throw new IllegalArgumentException("Crontab name, cronExpression and eventName are required");
        if (!CronExpression.isValidExpression(crontab.cronExpression))
            throw new IllegalArgumentException("Invalid cronExpression " + crontab.cronExpression);
        ZoneId.of(crontab.timezone);
        map(crontab.headersJson);
        object(crontab.eventPayloadJson);
    }

    private Map<String, String> map(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return mapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() { }); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid crontab JSON", e); }
    }
    private Map<String, Object> object(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try { return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { }); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid crontab JSON", e); }
    }
}
