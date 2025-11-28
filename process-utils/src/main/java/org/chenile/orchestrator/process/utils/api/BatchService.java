package org.chenile.orchestrator.process.utils.api;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;

public interface BatchService<T> {
	// The first trigger for the batch service.
	public Process doFirstTrigger(String initialProcessType,T input);
	// Starts the worker process
	public boolean startWorker(WorkerDto workerDto);
}
