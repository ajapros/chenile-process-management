package org.chenile.orchestrator.process.admin.configuration.controller;

import org.chenile.base.response.GenericResponse;
import org.chenile.core.annotation.ChenileBody;
import org.chenile.core.annotation.ChenileOperation;
import org.chenile.http.annotation.ChenileController;
import org.chenile.http.annotation.EventsSubscribedTo;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.springframework.http.ResponseEntity;


@ChenileController(value = "processWorkListener", serviceName = "_processWorkListener_")
public class ProcessWorkListener {
    @EventsSubscribedTo("process")
    @ChenileOperation
    public ResponseEntity<GenericResponse<Void>> startWork(
            @ChenileBody WorkerDto workerDto) {
        return null; // needs to do nothing since this method will never be called.
    }
}
