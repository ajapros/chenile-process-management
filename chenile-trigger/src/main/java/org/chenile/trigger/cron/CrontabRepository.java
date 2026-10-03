package org.chenile.trigger.cron;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CrontabRepository extends JpaRepository<Crontab, String>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Crontab> {
    List<Crontab> findByEnabledTrue();
}
