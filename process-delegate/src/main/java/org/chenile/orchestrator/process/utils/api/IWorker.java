package org.chenile.orchestrator.process.utils.api;

import org.chenile.orchestrator.process.model.WorkerDto;

public interface IWorker<Payload> {
    public void start(WorkerDto workerDto,Payload payload);
}
