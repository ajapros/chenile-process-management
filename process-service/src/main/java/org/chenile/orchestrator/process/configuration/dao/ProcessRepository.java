package org.chenile.orchestrator.process.configuration.dao;

import org.chenile.orchestrator.process.model.Process;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository  public interface ProcessRepository extends JpaRepository<Process,String> {
    List<Process> findByPredecessorId(String id);

    List<Process> findByPredecessorIdIsNotNull();

}
