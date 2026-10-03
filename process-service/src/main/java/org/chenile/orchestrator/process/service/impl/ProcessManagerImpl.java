package org.chenile.orchestrator.process.service.impl;

import org.chenile.base.exception.NotFoundException;
import org.chenile.core.context.ContextContainer;
import org.chenile.orchestrator.process.api.ProcessManager;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.config.reader.IProcessConfigurator;
import org.chenile.orchestrator.process.configuration.dao.ProcessRepository;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.ProcessDto;
import org.chenile.orchestrator.process.model.ProcessCompletedEvent;
import org.chenile.orchestrator.process.service.CompletionArguments;
import org.chenile.stm.STM;
import org.chenile.stm.impl.STMActionsInfoProvider;
import org.chenile.utils.entity.service.EntityStore;
import org.chenile.workflow.dto.StateEntityServiceResponse;
import org.chenile.workflow.service.impl.StateEntityServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.chenile.orchestrator.process.outbox.ProcessOutboxRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class ProcessManagerImpl extends StateEntityServiceImpl<Process> implements ProcessManager {
    @Autowired
    IProcessConfigurator processConfigurator;
    @Autowired
    ProcessRepository processRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ProcessOutboxRepository outbox;
    private TransactionTemplate transactions;

    @Autowired
    void configureDurability(ObjectProvider<ProcessOutboxRepository> repositories, PlatformTransactionManager manager) {
        outbox = repositories.getIfAvailable();
        if (outbox != null) transactions = new TransactionTemplate(manager);
    }

    private <T> T mutate(Supplier<T> mutation) {
        return transactions == null ? mutation.get() : transactions.execute(tx -> mutation.get());
    }
    /**
     * @param stm                    the state machine that has read the corresponding State Transition Diagram
     * @param stmActionsInfoProvider the provider that gives out info about the state diagram
     * @param entityStore            the store for persisting the entity
     */
    public ProcessManagerImpl(STM<Process> stm, STMActionsInfoProvider stmActionsInfoProvider,
                              EntityStore<Process> entityStore) {
        super(stm, stmActionsInfoProvider, entityStore);
    }

    @Override
    public StateEntityServiceResponse<Process> create(Process process) {
        return mutate(() -> {
            makeProcessLeafIfConfigured(process);
            return super.create(process);
        });
    }

    @Override
    public StateEntityServiceResponse<Process> create(ProcessDto processDto) {
        if (processDto == null || processDto.processDefName == null || processDto.processDefName.isBlank())
            throw new IllegalArgumentException("ProcessCreate requires processDefName");
        return mutate(() -> createFromDto(processDto, processDto.processDefName, null, null, processDto.args));
    }

    @Override
    public StateEntityServiceResponse<Process> processById(String id, String event, Object payload) {
        return mutate(() -> {
            if (outbox != null) processRepository.lockById(id);
            return super.processById(id, event, payload);
        });
    }

    @Override
    public StateEntityServiceResponse<Process> process(Process process, String event, Object payload) {
        if (outbox != null) return processById(process.getId(), event, payload);
        return super.process(process, event, payload);
    }

    @Override
    public List<StateEntityServiceResponse<Process>> processCompleted(ProcessCompletedEvent event) {
        if (event == null || event.processType == null || event.processType.isBlank())
            throw new IllegalArgumentException("ProcessCompleted requires processType");
        if (outbox != null && (event.processId == null || event.processId.isBlank()))
            throw new IllegalArgumentException("Durable ProcessCompleted requires processId");
        return mutate(() -> startSuccessors(event));
    }

    private List<StateEntityServiceResponse<Process>> startSuccessors(ProcessCompletedEvent event) {
        List<StateEntityServiceResponse<Process>> responses = new ArrayList<>();
        for (ProcessDef definition : processConfigurator.findByPredecessorProcessType(event.processType)) {
            if (outbox != null && !outbox.receive("CHAIN", receiptKey(event.processId, definition.processType))) continue;
            Map<String, Object> args = CompletionArguments.from(event.input, event.output, definition.predecessorArgs);
            responses.add(createFromDto(event, definition.processType, event.processId, event.tenantId, args));
        }
        return responses;
    }

    private String receiptKey(String predecessor, String successor) {
        try { return objectMapper.writeValueAsString(List.of(predecessor, successor)); }
        catch (Exception e) { throw new IllegalArgumentException("Cannot encode chaining identity", e); }
    }

    private StateEntityServiceResponse<Process> createFromDto(ProcessDto processDto, String processDefName,
                                                              String predecessorId, String tenantId,
                                                              Map<String, Object> args) {
        String triggerId = processDto.triggerId;
        if (triggerId == null || triggerId.isBlank())
            triggerId = ContextContainer.getInstance().get("x-chenile-trigger-id");
        if (triggerId != null && triggerId.isBlank()) triggerId = null;
        Process process = new Process(processDefName, false);
        process.predecessorId = predecessorId;
        if (tenantId != null) process.tenant = tenantId;
        process.triggerId = triggerId;
        process.description = processDto.description;
        try {
            process.input = objectMapper.writeValueAsString(args == null ? Map.of() : args);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot serialize ProcessCreate arguments", e);
        }
        return create(process);
    }

    private void makeProcessLeafIfConfigured(Process process){
        if(process.leaf || process.processType == null) return;
        ProcessDef processDef = processConfigurator.findByName(process.processType);
        if (processDef == null) return;
        process.leaf = processDef.leaf;
    }
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Process> getSubProcesses(String processId, boolean recursive){
        StateEntityServiceResponse<Process> response = retrieve(processId);
        if (response == null)
            throw new NotFoundException("40001","Missing process " + processId);
        Process process = response.getMutatedEntity();
        List<Process> childProcesses = new ArrayList<>();
        childProcesses.add(process);
        getSubProcesses(childProcesses,process,recursive);
        // Workers and HTTP callers consume detached snapshots after this read transaction ends.
        // Process.errors is eager, but the structured validation messages inside each error are lazy.
        for (Process child : childProcesses)
            for (var error : child.errors)
                if (error.errors != null) error.errors.size();
        return childProcesses;
    }

    private void getSubProcesses(List<Process> childProcesses,Process process,boolean recursive){
        List<Process> processList =  processRepository.findByParentId(process.getId());
        if (processList == null || processList.isEmpty()) return;
        childProcesses.addAll(processList);
        if(!recursive) return;
        for (Process p: processList){
            getSubProcesses(childProcesses,p,recursive);
        }
    }
}
