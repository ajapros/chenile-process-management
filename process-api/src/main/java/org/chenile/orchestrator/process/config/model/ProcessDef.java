package org.chenile.orchestrator.process.config.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

public class ProcessDef {
    private String parentProcessType;
    private String processType;
    private boolean leaf;
    private final Map<String, String> splitterConfig = new HashMap<>();
    private final Map<String, String> aggregatorConfig = new HashMap<>();
    private final Map<String, String> executorConfig = new HashMap<>();
    private final Map<String, String> chainerConfig = new HashMap<>();

    // No-argument constructor for Jackson
    public ProcessDef() {
    }

    // Parameterized constructor
    @JsonCreator
    public ProcessDef(
            @JsonProperty("parentProcessType") String parentProcessType,
            @JsonProperty("processType") String processType,
            @JsonProperty("leaf") boolean leaf,
            @JsonProperty("splitterConfig") Map<String, String> splitterConfig,
            @JsonProperty("aggregatorConfig") Map<String, String> aggregatorConfig,
            @JsonProperty("executorConfig") Map<String, String> executorConfig,
            @JsonProperty("chainerConfig") Map<String, String> chainerConfig) {
        this.parentProcessType = parentProcessType;
        this.processType = processType;
        this.leaf = leaf;
        if (splitterConfig != null) this.splitterConfig.putAll(splitterConfig);
        if (aggregatorConfig != null) this.aggregatorConfig.putAll(aggregatorConfig);
        if (executorConfig != null) this.executorConfig.putAll(executorConfig);
        if (chainerConfig != null) this.executorConfig.putAll(chainerConfig);
    }

    public String getParentProcessType() {
        return parentProcessType;
    }

    public void setParentProcessType(String parentProcessType) {
        this.parentProcessType = parentProcessType;
    }

    public String getProcessType() {
        return processType;
    }

    public void setProcessType(String processType) {
        this.processType = processType;
    }

    public boolean isLeaf() {
        return leaf;
    }

    public void setLeaf(boolean leaf) {
        this.leaf = leaf;
    }

    public Map<String, String> getSplitterConfig() {
        return splitterConfig;
    }

    public void setSplitterConfig(Map<String, String> splitterConfig) {
        this.splitterConfig.clear();
        if (splitterConfig != null) this.splitterConfig.putAll(splitterConfig);
    }

    public Map<String, String> getAggregatorConfig() {
        return aggregatorConfig;
    }

    public void setAggregatorConfig(Map<String, String> aggregatorConfig) {
        this.aggregatorConfig.clear();
        if (aggregatorConfig != null) this.aggregatorConfig.putAll(aggregatorConfig);
    }

    public Map<String, String> getExecutorConfig() {
        return executorConfig;
    }

    public void setExecutorConfig(Map<String, String> executorConfig) {
        this.executorConfig.clear();
        if (executorConfig != null) this.executorConfig.putAll(executorConfig);
    }

    public Map<String, String> getChainerConfig() {
        return chainerConfig;
    }

    public void setChainerConfig(Map<String, String> chainerConfig) {
        this.chainerConfig.clear();
        if (chainerConfig != null) this.chainerConfig.putAll(chainerConfig);
    }
}