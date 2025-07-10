package org.chenile.orchestrator.process.feedtest;

import org.chenile.orchestrator.process.service.defs.PostSaveHook;
import org.chenile.orchestrator.process.service.defs.ProcessConfigurator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

@Configuration
@SpringBootApplication(scanBasePackages = { "org.chenile.configuration" , "org.chenile.orchestrator.process.configuration"})
@PropertySource("classpath:org/chenile/orchestrator/process/TestService.properties")
@EntityScan({"org.chenile.orchestrator.**.model"})
@EnableJpaRepositories(basePackages = {"org.chenile.orchestrator.**.configuration.dao"})
@ActiveProfiles("unittest")
public class SpringConfig extends SpringBootServletInitializer{

    @Bean
    InVMWorkerStarterDelegator inVMProcessStarterDelegator(
            @Qualifier("postSaveHook") PostSaveHook postSaveHook,
		    @Qualifier("processConfigurator")ProcessConfigurator processConfigurator){
        InVMWorkerStarterDelegator workerStarter = new InVMWorkerStarterDelegator();
        postSaveHook.setWorkerStarter(workerStarter);
        processConfigurator.read("def.json");
        return workerStarter;
    }

    /*
     * The beans below are named appropriately so they can be auto discovered as
     * Process Starters. These are invoked by the post save hook AFTER the Process is
     * saved in the DB with the given state.
     */

    @Bean
    ChunkExecutor chunkExecutor(){
        return new ChunkExecutor();
    }

    @Bean
    FileChunker fileSplitter(){
        return new FileChunker();
    }

    @Bean
    FileAggregator fileAggregator(){
        return new FileAggregator();
    }

    @Bean
    FeedSplitter feedSplitter(){
        return new FeedSplitter();
    }

    @Bean
    FeedAggregator feedAggregator(){
        return new FeedAggregator();
    }
}

