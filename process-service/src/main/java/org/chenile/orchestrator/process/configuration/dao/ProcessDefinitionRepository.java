package org.chenile.orchestrator.process.configuration.dao;

import org.chenile.orchestrator.process.configuration.model.ProcessDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessDefinitionRepository extends JpaRepository<ProcessDefinition, String> {
}
