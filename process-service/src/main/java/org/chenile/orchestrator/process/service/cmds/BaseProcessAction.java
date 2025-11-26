package org.chenile.orchestrator.process.service.cmds;

import org.apache.commons.lang3.StringUtils;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.payload.ErrorPayload;
import org.chenile.orchestrator.process.model.payload.SubProcessError;
import org.chenile.workflow.service.stmcmds.AbstractSTMTransitionAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public abstract class BaseProcessAction<T> extends AbstractSTMTransitionAction<Process, T> {
    private static final Logger logger = LoggerFactory.getLogger(BaseProcessAction.class);

    protected void addErrorToProcess(Process process, ErrorPayload payload) {
        if (process == null || payload == null) {
            return;
        }
        boolean hasErrors =
                (payload.errors != null && !payload.errors.isEmpty()) ||
                        StringUtils.isNotBlank(payload.exceptionMessage) ||
                        StringUtils.isNotBlank(payload.stackTrace);

        if (!hasErrors) {
            return; // nothing to record
        }

        SubProcessError subProcessError = new SubProcessError();
        subProcessError.processId = process.getId();
        subProcessError.timeOfCompletion = new Date();
        subProcessError.errors = payload.errors;
        subProcessError.exceptionMessage = payload.exceptionMessage;
        subProcessError.stackTrace = payload.stackTrace;
        process.errors.add(subProcessError);
    }
}
