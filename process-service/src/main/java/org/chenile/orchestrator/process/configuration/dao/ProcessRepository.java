package org.chenile.orchestrator.process.configuration.dao;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.chenile.orchestrator.process.model.Process;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessRepository
    extends JpaRepository<Process, String>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Process> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Process p where p.id = :id")
  Optional<Process> lockById(@Param("id") String id);

  List<Process> findByPredecessorId(String id);

  List<Process> findByPredecessorIdIsNotNull();

  List<Process> findByParentId(String id);
}
