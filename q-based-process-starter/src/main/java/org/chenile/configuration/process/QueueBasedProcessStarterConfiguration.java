package org.chenile.configuration.process;

import org.chenile.orchestrator.process.QueueBasedProcessStarter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueueBasedProcessStarterConfiguration {
    final Logger logger = LoggerFactory.getLogger(QueueBasedProcessStarterConfiguration.class);
    @Bean
    QueueBasedProcessStarter queueBasedProcessStarter(){
        logger.info("Initializing the queue based worker starter");
        QueueBasedProcessStarter workerStarter = new QueueBasedProcessStarter();
        return workerStarter;
    }
}
