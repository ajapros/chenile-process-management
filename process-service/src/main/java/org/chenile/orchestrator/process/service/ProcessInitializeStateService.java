package org.chenile.orchestrator.process.service;

import org.chenile.orchestrator.process.model.Process;
import jakarta.transaction.Transactional;
import org.chenile.orchestrator.process.configuration.dao.ProcessRepository;
import org.springframework.beans.factory.annotation.Autowired;

public class ProcessInitializeStateService {
    private final ProcessRepository processRepository;

    public ProcessInitializeStateService(ProcessRepository processRepository) {
        this.processRepository = processRepository;
    }

    /**
     * Finds a process, adds the given state to its initialized set,
     * and saves the change to the database in a new transaction.
     */
    @Transactional
    public void markStateAsInitialized(String processId, String stateId) {
        Process process = processRepository.findById(processId).orElse(null);
        if (process != null) {
            process.initializedStates.add(stateId);
            processRepository.save(process);
        }
    }
}
