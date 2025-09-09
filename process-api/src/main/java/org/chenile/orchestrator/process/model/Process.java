package org.chenile.orchestrator.process.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.chenile.jpautils.entity.AbstractJpaStateEntity;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "process_table")
public class Process extends AbstractJpaStateEntity {

    public Process() {
        leaf = false;
    }

    public Process(boolean... isLeaf) {
        if (isLeaf != null && isLeaf.length == 1)
            this.leaf = isLeaf[0];
    }

    public Process(String processType, boolean... isLeaf) {
        this(isLeaf);
        this.processType = processType;
    }

    /**
     * // is this leaf process.
     */
    private boolean leaf;

    /**
     * the client is stored as part of the process.<br/>
     * this enables us to use the client ID to enable client isolation.
     */
    private String clientId;

    private String processType;

    private int completedPercent = 0;

    private String parentId;

    /**
     * Primarily FYI
     */
    private String description;

    /**
     * All errors encountered in this process and its sub-processes.<br/>
     * This will ultimately be consolidated in the top level.<br/>
     * Avoid storing the actual errors here. Instead, use this to point to a file that
     * actually contains the errors.
     */
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private List<SubProcessError> errors = new ArrayList<>();

    private int numSubProcesses = 0;

    private int numCompletedSubProcesses = 0;

    @Column(name = "is_split_complete")
    private boolean isSplitComplete = false;


/*    @Transient
    private List<Process> subProcesses = new ArrayList<>();*/

    /**
     * All sub processes are pointed to here.<br/>
     * This will not be persisted. It is useful to persist the subprocesses after saving the
     * current process
     */
    @Transient
    private List<Process> subProcessesToCreate = new ArrayList<>();

    /**
     * A transient list of sequential processes for the next phase.
     * This is populated by the Aggregator and used by the CHAIN_TRIGGER.
     */
    @Transient
    private List<Process> successorsToCreate = new ArrayList<>();



    /**
     * Arguments required to start this process.<br/>
     * This is stored as a String since it needs to be persisted into the
     * database or transmitted over the wire since the sub-process will be created in a
     * separate VM
     */
    @Column(name = "args", columnDefinition = "TEXT")
    private String args;

    // Getters and Setters

    public boolean isLeaf() {
        return leaf;
    }

    public void setLeaf(boolean leaf) {
        this.leaf = leaf;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getProcessType() {
        return processType;
    }

    public void setProcessType(String processType) {
        this.processType = processType;
    }

    public int getCompletedPercent() {
        return completedPercent;
    }

    public void setCompletedPercent(int completedPercent) {
        this.completedPercent = completedPercent;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<SubProcessError> getErrors() {
        return errors;
    }

    public void setErrors(List<SubProcessError> errors) {
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
    }

    public int getNumSubProcesses() {
        return numSubProcesses;
    }

    public void setNumSubProcesses(int numSubProcesses) {
        this.numSubProcesses = numSubProcesses;
    }

    public int getNumCompletedSubProcesses() {
        return numCompletedSubProcesses;
    }

    public void setNumCompletedSubProcesses(int numCompletedSubProcesses) {
        this.numCompletedSubProcesses = numCompletedSubProcesses;
    }

    public boolean isSplitComplete() {
        return isSplitComplete;
    }

    public void setSplitComplete(boolean splitComplete) {
        isSplitComplete = splitComplete;
    }

    public List<Process> getSubProcessesToCreate() {
        return subProcessesToCreate;
    }

    public void setSubProcessesToCreate(List<Process> subProcessesToCreate) {
        this.subProcessesToCreate = subProcessesToCreate != null ? new ArrayList<>(subProcessesToCreate) : new ArrayList<>();
    }

    public List<Process> getSuccessorsToCreate() {
        return successorsToCreate;
    }

    public void setSuccessorsToCreate(List<Process> successorsToCreate) {
        this.successorsToCreate = successorsToCreate != null ? new ArrayList<>(successorsToCreate) : new ArrayList<>();
    }

    /**
     * A convenience method used by the state machine's 'if' condition
     * to determine if a next phase needs to be triggered.
     * @return true if there are sequential processes planned for creation.
     */
    public boolean hasSuccessor() {
        return this.successorsToCreate != null && !this.successorsToCreate.isEmpty();
    }

    public String getArgs() {
        return args;
    }

    public void setArgs(String args) {
        this.args = args;
    }

    public boolean isAllSubProcessesCompleted() {
        return getNumCompletedSubProcesses() == getNumSubProcesses();
    }
}