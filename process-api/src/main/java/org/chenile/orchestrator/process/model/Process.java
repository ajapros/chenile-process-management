package org.chenile.orchestrator.process.model;

import jakarta.persistence.*;
import org.chenile.jpautils.entity.AbstractJpaStateEntity;

import java.util.ArrayList;
import java.util.List;

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
	 * the client is stored as part of the process.<br/>
	 * this enables us to use the client ID to enable client isolation.
	 */
	public String clientId;
	public String processType;
	public int completedPercent = 0;
	public String parentId;
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
	 * This will not be persisted. It is useful to persist the subprocesses after saving the
	 * current process
	 */
	@Transient
	public List<Process> subProcesses = new ArrayList<>();
	/**
		Arguments required to start this process.<br/>
	 	This is stored as a String since it needs to be persisted into the
	 	database or transmitted over the wire since the sub-process will be created in a
	 	separate VM
	 */
	public String args ;
}
