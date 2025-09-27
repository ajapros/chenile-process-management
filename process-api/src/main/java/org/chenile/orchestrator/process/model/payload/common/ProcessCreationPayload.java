package org.chenile.orchestrator.process.model.payload.common;

import org.chenile.workflow.param.MinimalPayload;

public class ProcessCreationPayload extends MinimalPayload {
    private String workerSuppliedId;
    private String args;
    private String processType;
    private boolean leaf;

    public String getWorkerSuppliedId() {
        return workerSuppliedId;
    }

    public void setWorkerSuppliedId(String workerSuppliedId) {
        this.workerSuppliedId = workerSuppliedId;
    }
    public String getArgs() {
        return args;
    }

    public void setArgs(String args) {
        this.args = args;
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

}
