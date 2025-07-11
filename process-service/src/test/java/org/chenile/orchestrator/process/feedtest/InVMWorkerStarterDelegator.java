package org.chenile.orchestrator.process.feedtest;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.model.WorkerType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.Map;

/**
 * Starts a process within the same JVM by using a component that has been
 * defined within the Spring Bean factory. This delegates to the correct component name<br/>
 * The name of the bean is determined by convention.
 * The bean is supposed to implement the ProcessStarter interface as well.
 */
public class InVMWorkerStarterDelegator implements WorkerStarter {
    @Autowired
    ApplicationContext applicationContext;
    @Override
    public void start(Process process, Map<String, String> execDef, WorkerType workerType) {
        String componentName = process.processType + camelCase(workerType);
        try {
            WorkerStarter workerStarter = (WorkerStarter) applicationContext.getBean(componentName);
            workerStarter.start(process, execDef,workerType);
        }catch(Exception ignore){}
    }

    private String camelCase(WorkerType workerType){
        String s =  workerType.name().toLowerCase();
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }
}
