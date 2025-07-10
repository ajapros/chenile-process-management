package org.chenile.orchestrator.process.configuration.dao;

import org.chenile.orchestrator.process.model.Process;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository  public interface ProcessRepository extends JpaRepository<Process,String> {}
