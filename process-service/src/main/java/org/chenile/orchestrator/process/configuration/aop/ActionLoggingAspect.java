package org.chenile.orchestrator.process.configuration.aop;

import org.chenile.orchestrator.process.model.Process;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.chenile.stm.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ActionLoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(ActionLoggingAspect.class);

    // in any class that extends AbstractSTMTransitionAction.
    @Around("execution(public void *.transitionTo(..)) && within(org.chenile.workflow.service.stmcmds.AbstractSTMTransitionAction+)")
    public Object logActionInvocation(ProceedingJoinPoint joinPoint) throws Throwable {

        Object[] args = joinPoint.getArgs();
        Process process = (Process) args[0];
        State startState = (State) args[2];
        String eventId = (String) args[3];
        State endState = (State) args[4];
        String actionClassName = joinPoint.getTarget().getClass().getSimpleName();

        logger.debug("ACTION INVOKED: {} for processId={}, event='{}' (Transitioning: {} -> {})",
                actionClassName,
                process.getId(),
                eventId,
                startState != null ? startState.getStateId() : "null",
                endState != null ? endState.getStateId() : "null"
        );
        return joinPoint.proceed();
    }
}