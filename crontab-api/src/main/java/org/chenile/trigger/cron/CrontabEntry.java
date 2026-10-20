package org.chenile.trigger.cron;

import java.util.Map;

/** JSON schedule document entry, allowing structured alternatives to encoded JSON fields. */
public class CrontabEntry extends Crontab {
  public Map<String, String> headers;
  public Map<String, Object> payload;
}
