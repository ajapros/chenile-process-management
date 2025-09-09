package org.chenile.orchestrator.process.config.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Processes {
    private final Map<String, ProcessDef> processMap = new HashMap<>();

    public Map<String, ProcessDef> getProcessMap() {
        return Collections.unmodifiableMap(processMap);
    }

    public ProcessDef getProcess(String name) {
        return processMap.get(name);
    }

    public void addProcess(String name, ProcessDef processDef) {
        processMap.put(name, processDef);
    }

    public void removeProcess(String name) {
        processMap.remove(name);
    }
}