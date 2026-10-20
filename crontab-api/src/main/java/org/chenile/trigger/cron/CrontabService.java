package org.chenile.trigger.cron;

import java.util.List;

public interface CrontabService {
  Crontab save(Crontab crontab);

  Crontab retrieve(String id);

  void delete(String id);

  List<Crontab> findAll();
}
