package org.chenile.orchestrator.process.configuration.dao;

import org.chenile.orchestrator.process.configuration.model.CompletionEventRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface CompletionEventRepository extends JpaRepository<CompletionEventRecord, String> {
    List<CompletionEventRecord> findByTenantAndProcessIdIn(String tenant, Collection<String> ids);
}
