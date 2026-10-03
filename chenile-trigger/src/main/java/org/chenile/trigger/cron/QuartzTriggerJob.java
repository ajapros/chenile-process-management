package org.chenile.trigger.cron;

import org.quartz.Job;
import org.quartz.JobExecutionContext;

public class QuartzTriggerJob implements Job {
    static final String SCHEDULER = "chenile.trigger.scheduler";
    static final String CRONTAB_ID = "chenile.trigger.crontab.id";

    @Override public void execute(JobExecutionContext context) {
        CrontabScheduler scheduler = (CrontabScheduler) context.getMergedJobDataMap().get(SCHEDULER);
        scheduler.fire((String) context.getMergedJobDataMap().get(CRONTAB_ID), context);
    }
}
