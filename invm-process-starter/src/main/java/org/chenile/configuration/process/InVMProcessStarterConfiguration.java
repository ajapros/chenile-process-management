package org.chenile.configuration.process;

import org.chenile.orchestrator.process.InVMProcessStarter;
import org.chenile.orchestrator.process.service.defs.PostSaveHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InVMProcessStarterConfiguration {
    final Logger logger = LoggerFactory.getLogger(InVMProcessStarterConfiguration.class);
    @Bean
    InVMProcessStarter inVMProcessStarter(
            @Qualifier("postSaveHook") PostSaveHook postSaveHook){
        logger.info("Initializing the in vm worker starter");
        InVMProcessStarter workerStarter = new InVMProcessStarter();
        postSaveHook.setWorkerStarter(workerStarter);
        return workerStarter;
    }
}
