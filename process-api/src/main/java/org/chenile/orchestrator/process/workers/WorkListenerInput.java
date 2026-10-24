package org.chenile.orchestrator.process.workers;

import java.util.Map;

public class WorkListenerInput<PayloadType> {
    public String processId;
    public String dispatchId;
    public PayloadType payload;
    public Map<String,String> execDef;
}
