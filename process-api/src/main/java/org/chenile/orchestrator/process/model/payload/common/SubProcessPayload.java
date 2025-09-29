package org.chenile.orchestrator.process.model.payload.common;

import org.chenile.workflow.param.MinimalPayload;

public class SubProcessPayload extends MinimalPayload {
    public String workerSuppliedId; // inject the id for the child. Useful for tests
    public String args;
    public String processType;
    public boolean leaf = false;
    public boolean dormant = false;
    public String predecessorId;

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

    public boolean isDormant() {
        return dormant;
    }

    public void setDormant(boolean dormant) {
        this.dormant = dormant;
    }

    public String getPredecessorId() {
        return predecessorId;
    }

    public void setPredecessorId(String predecessorId) {
        this.predecessorId = predecessorId;
    }
}
