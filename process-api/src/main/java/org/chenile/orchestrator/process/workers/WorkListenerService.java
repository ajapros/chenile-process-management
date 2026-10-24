package org.chenile.orchestrator.process.workers;

public interface WorkListenerService<PayloadType> {
    boolean accept(String processType, String workerType, WorkListenerInput<PayloadType> input);
}
