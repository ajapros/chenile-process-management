package org.chenile.orchestrator.process.test;

import org.chenile.orchestrator.process.WorkerStarter;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.WorkerType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.chenile.orchestrator.process.test.SharedData.LATCHES;
import static org.chenile.orchestrator.process.test.SharedData.SYNCH_MODE;
/**
 * Starts a process within the same JVM by using a component that has been
 * defined within the Spring Bean factory. This delegates to the correct component name<br/>
 * The name of the actual worker is determined by convention.
 * The actual worker is supposed to implement the {@link WorkerStarter} interface as well.
 */
public class InVMWorkerStarterDelegator implements WorkerStarter {
    @Autowired
    ApplicationContext applicationContext;
    ExecutorService executorService = Executors.newFixedThreadPool(20);
    @Override
    public void start(WorkerDto workerDto) {
        String componentName = workerDto.process.processType + camelCase(workerDto.workerType);
        try {
            WorkerStarter actualWorker = (WorkerStarter) applicationContext.getBean(componentName);
            if(SYNCH_MODE)
                doSynchStart(actualWorker,workerDto);
            else
                doAsynchStart(actualWorker,workerDto);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    protected void doAsynchStart(WorkerStarter actualWorker,WorkerDto workerDto) {
        executorService.submit(() -> {
            try {
                String latchKey = workerDto.process.id + "-" + workerDto.workerType.name();
                String proceedKey = latchKey + "PROCEED";
                CountDownLatch latch = new CountDownLatch(1);
                LATCHES.put(latchKey , latch);
                latch.await();
                actualWorker.start(workerDto);
                LATCHES.get(proceedKey).countDown();
            }catch (Exception e){
                e.printStackTrace();
            }
        });
    }

    protected void doSynchStart(WorkerStarter actualWorker,WorkerDto workerDto) {
        actualWorker.start(workerDto);
    }

    private String camelCase(WorkerType workerType){
        String s =  workerType.name().toLowerCase();
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }
}
