package org.chenile.trigger.cron;

import java.util.List;
import java.util.Optional;

/** Read contract shared by database and JSON schedule repositories. */
public interface CrontabReader {
    List<Crontab> findAll();
    List<Crontab> findByEnabledTrue();
    Optional<Crontab> findById(String id);
}
