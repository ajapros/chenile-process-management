package org.chenile.orchestrator.process.service.cmds;

import org.apache.commons.lang3.StringUtils;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.SubProcessError;
import org.chenile.orchestrator.process.model.payload.ErrorPayload;
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
                (payload.getValidationErrors() != null && !payload.getValidationErrors().isEmpty()) ||
                        StringUtils.isNotBlank(payload.getExceptionMessage()) ||
                        StringUtils.isNotBlank(payload.getStackTrace());

        if (!hasErrors) {
            return; // nothing to record
        }

        SubProcessError subProcessError = new SubProcessError();
        subProcessError.setProcessId(process.getId());
        subProcessError.setTimeOfCompletion(new Date());
        subProcessError.setValidationErrors(payload.getValidationErrors());
        subProcessError.setExceptionMessage(payload.getExceptionMessage());
        subProcessError.setStackTrace(payload.getStackTrace());
        process.errors.add(subProcessError);
    }
}
