package org.chenile.orchestrator.process.model;

import io.micrometer.common.util.StringUtils;

public enum WorkerType {
    SPLITTER("Splitter") ,
    AGGREGATOR("Aggregator"),
    EXECUTOR("Executor");

    private final String beanSuffix;

    WorkerType(String beanSuffix) {
        this.beanSuffix = beanSuffix;
    }

    public String getBeanSuffix() {
        return this.beanSuffix;
    }

    /**
     * Constructs the full Spring bean name for a specific worker by combining
     * a process type prefix with the worker's defined suffix.
     * <p>
     * Example: getComponentName("loanMinimalFeed") -> "loanMinimalFeedSplitter"
     *
     * @param processTypePrefix The prefix for the bean name, typically the process type (e.g., "loanMinimalFeed").
     * @return The fully constructed component name.
     */
    public String getComponentName(String processTypePrefix) {
        if (StringUtils.isBlank(processTypePrefix)) {
            processTypePrefix = "default";
        }
        return processTypePrefix + this.beanSuffix;
    }
}
