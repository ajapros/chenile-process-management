package org.chenile.orchestrator.process.service.outbox;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.stm.State;

/** Producer context, not part of the generic delivery schema. */
public record ProcessTransition(Process process, State state) { }
