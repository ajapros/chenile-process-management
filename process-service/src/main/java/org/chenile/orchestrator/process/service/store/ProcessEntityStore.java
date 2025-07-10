package org.chenile.orchestrator.process.service.store;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.utils.entity.service.EntityStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.chenile.base.exception.NotFoundException;
import org.chenile.orchestrator.process.configuration.dao.ProcessRepository;
import java.util.Optional;

public class ProcessEntityStore implements EntityStore<Process>{
    @Autowired private ProcessRepository processRepository;

	@Override
	public void store(Process entity) {
        processRepository.save(entity);
	}

	@Override
	public Process retrieve(String id) {
        Optional<Process> entity = processRepository.findById(id);
        if (entity.isPresent()) return entity.get();
        throw new NotFoundException(1500,"Unable to find Process with ID " + id);
	}

}
