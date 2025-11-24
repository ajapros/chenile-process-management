package org.chenile.orchestrator.process.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.chenile.jpautils.entity.AbstractJpaStateEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "process_table")
public class Process extends AbstractJpaStateEntity
{
	public Process(){
		leaf = false;
	}
	public Process(boolean... isLeaf){
		if(isLeaf != null && isLeaf.length == 1)
			this.leaf = isLeaf[0];
	}
	public Process(String processType, boolean... isLeaf){
		this(isLeaf);
		this.processType = processType;
	}

	/**
	 * // is this leaf process.
	 */
	public boolean leaf;
	/**
	 * is this a dormant process
	 */
	public boolean dormant = false;
	/**
	 * the client is stored as part of the process.<br/>
	 * this enables us to use the client ID to enable client isolation.
	 */
	public String clientId;
	public String processType;
	/**
	 * Has the split completed? Split will be completed only after the splitDone event is
	 * called.
	 */
	public boolean splitCompleted;
	public int completedPercent = 0;
	public String parentId;
	@Transient
	public String childIdToActivateSuccessors;
	/**
	 * Primarily FYI
	 */
	public String description;
	/**
	 * All errors encountered in this process and its sub-processes.<br/>
	 * This will ultimately be consolidated in the top level.<br/>
	 * Avoid storing the actual errors here. Instead, use this to point to a file that
	 * actually contains the errors.
	 */
	@OneToMany(cascade = CascadeType.ALL,fetch = FetchType.EAGER,orphanRemoval = true)
	public List<SubProcessError> errors = new ArrayList<>();
	public int numSubProcesses = 0;
	public int numCompletedSubProcesses = 0;
	/**
	 * All sub processes are pointed to here.<br/>
	 * This will not be persisted. The sub processes will be manually persisted after the current
	 * process is saved.
	 */
	@Transient
	public List<Process> subProcesses = new ArrayList<>();
	/**
		Arguments required to start this process.<br/>
	 	This is stored as a String since it needs to be persisted into the
	 	database or transmitted over the wire since the sub-process will be created in a
	 	separate VM
	 */
    @Column(name = "input", columnDefinition = "TEXT")
    public String input ;
    /**
     * Output of the process. Computed by the executors or the Aggregators.
     * The output will be rolled up to the parent process.
     */
    public String output;
	/**
	 * The flag below is useful to skip worker creation when status updates are done.
	 */
	//@Transient public boolean skipPostWorkerCreation = false;
	public String predecessorId;

    @ElementCollection(fetch = FetchType.EAGER)
    public Set<String> initializedStates = new HashSet<>();

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

    public boolean isSplitCompleted() {
        return splitCompleted;
    }

    public void setSplitCompleted(boolean splitCompleted) {
        this.splitCompleted = splitCompleted;
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

    public String getChildIdToActivateSuccessors() {
        return childIdToActivateSuccessors;
    }

    public void setChildIdToActivateSuccessors(String childIdToActivateSuccessors) {
        this.childIdToActivateSuccessors = childIdToActivateSuccessors;
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
        this.errors = errors;
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

    public List<Process> getSubProcesses() {
        return subProcesses;
    }

    public void setSubProcesses(List<Process> subProcesses) {
        this.subProcesses = subProcesses;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String args) {
        this.input = args;
    }

    public String getPredecessorId() {
        return predecessorId;
    }

    public void setPredecessorId(String predecessorId) {
        this.predecessorId = predecessorId;
    }

    public Set<String> getInitializedStates() {
        return initializedStates;
    }

    public void setInitializedStates(Set<String> initializedStates) {
        this.initializedStates = initializedStates;
    }
}
