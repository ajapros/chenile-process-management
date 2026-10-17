package org.chenile.trigger.cron;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.quartz.CronExpression;
import java.time.ZoneId;
import java.util.Map;

/** Schedule validation used by both repository sources and the scheduler. */
public final class CrontabValidator {
    private static final ObjectMapper JSON = new ObjectMapper();
    private CrontabValidator() {}

    public static void validate(Crontab cron) {
        if (cron == null || cron.name == null || cron.name.isBlank() || cron.name.length() > 255
                || cron.cronExpression == null || cron.eventName == null || cron.eventName.isBlank())
            throw new IllegalArgumentException("Crontab name, cronExpression and eventName are required");
        if (!CronExpression.isValidExpression(cron.cronExpression))
            throw new IllegalArgumentException("Invalid cronExpression " + cron.cronExpression);
        ZoneId.of(cron.timezone);
        if (cron.tenant != null && !cron.tenant.matches("[A-Za-z0-9_.-]{1,128}"))
            throw new IllegalArgumentException("Invalid cron tenant");
        try {
            if (cron.headersJson != null && !cron.headersJson.isBlank()) {
                var headers = JSON.readValue(cron.headersJson, new TypeReference<Map<String,String>>() {});
                if (headers == null) throw new IllegalArgumentException("Headers must be a JSON object");
                for (String key : headers.keySet())
                    if (!key.toLowerCase(java.util.Locale.ROOT).startsWith("x-"))
                        throw new IllegalArgumentException("Crontab headers must start with x-");
            }
        } catch (Exception e) { throw new IllegalArgumentException("Invalid crontab JSON", e); }
    }
}
