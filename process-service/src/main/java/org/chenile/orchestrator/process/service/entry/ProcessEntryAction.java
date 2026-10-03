package org.chenile.orchestrator.process.service.entry;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.service.outbox.ProcessEffects;
import org.chenile.stm.State;
import org.chenile.stm.impl.STMActionsInfoProvider;
import org.chenile.utils.entity.service.EntityStore;
import org.chenile.workflow.service.stmcmds.GenericEntryAction;
import org.springframework.beans.factory.annotation.Autowired;

/** Save first, then use the same registered commands in both inline and durable modes. */
public class ProcessEntryAction extends GenericEntryAction<Process> {
    @Autowired private ProcessEffects effects;

    public ProcessEntryAction(EntityStore<Process> entityStore, STMActionsInfoProvider stmActionsInfoProvider) {
        super(entityStore, stmActionsInfoProvider);
    }

    @Override public void execute(State fromState, State toState, Process process) throws Exception {
        super.execute(fromState, toState, process);
        effects.enqueue(process, toState);
    }
}
