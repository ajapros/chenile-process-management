package org.chenile.orchestrator.process.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Payload for creating a root process through HTTP or the ProcessCreate event. */
public class ProcessDto {
    public String triggerId;
    public String processDefName;
    public Map<String, Object> args = new LinkedHashMap<>();
    public String description;
}
