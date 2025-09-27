package org.chenile.orchestrator.process.configuration;

import org.chenile.orchestrator.process.service.defs.PostSaveHook;
import org.chenile.orchestrator.process.service.defs.ProcessConfigurator;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.service.entry.NotifyParent;
import org.chenile.orchestrator.process.service.entry.ProcessEntryAction;
import org.chenile.orchestrator.process.service.cmds.*;
import org.chenile.orchestrator.process.service.impl.ProcessManager;
import org.chenile.stm.*;
import org.chenile.stm.action.STMTransitionAction;
import org.chenile.stm.impl.*;
import org.chenile.stm.spring.SpringBeanFactoryAdapter;
import org.chenile.workflow.api.StateEntityService;
import org.chenile.workflow.param.MinimalPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.chenile.utils.entity.service.EntityStore;
import org.chenile.workflow.service.impl.StateEntityServiceImpl;
import org.chenile.workflow.service.stmcmds.*;
import org.chenile.orchestrator.process.service.healthcheck.ProcessHealthChecker;
import org.chenile.orchestrator.process.service.store.ProcessEntityStore;
import org.chenile.workflow.api.WorkflowRegistry;


/**
 Process related workflow configurations in Spring
*/
@Configuration
public class ProcessConfiguration {
	private static final String FLOW_DEFINITION_FILE = "org/chenile/orchestrator/process/process-states.xml";
	public static final String PREFIX_FOR_PROPERTIES = "Process";
    public static final String PREFIX_FOR_RESOLVER = "process";

    @Bean BeanFactoryAdapter processBeanFactoryAdapter() {
		return new SpringBeanFactoryAdapter();
	}
	
	@Bean STMFlowStoreImpl processFlowStore(
            @Qualifier("processBeanFactoryAdapter") BeanFactoryAdapter fileBeanFactoryAdapter
            )throws Exception{
		STMFlowStoreImpl stmFlowStore = new STMFlowStoreImpl();
		stmFlowStore.setBeanFactory(fileBeanFactoryAdapter);
		return stmFlowStore;
	}
	
	@Bean @Autowired STM<Process> processEntityStm(@Qualifier("processFlowStore") STMFlowStoreImpl stmFlowStore) throws Exception{
		STMImpl<Process> stm = new STMImpl<>();
		stm.setStmFlowStore(stmFlowStore);
		return stm;
	}
	
	@Bean @Autowired STMActionsInfoProvider processActionsInfoProvider(@Qualifier("processFlowStore") STMFlowStoreImpl stmFlowStore) {
		STMActionsInfoProvider provider =  new STMActionsInfoProvider(stmFlowStore);
        WorkflowRegistry.addSTMActionsInfoProvider("process",provider);
        return provider;
	}
	
	@Bean EntityStore<Process> processEntityStore() {
		return new ProcessEntityStore();
	}
	
	@Bean @Autowired StateEntityServiceImpl<Process> _processStateEntityService_(
			@Qualifier("processEntityStm") STM<Process> stm,
			@Qualifier("processActionsInfoProvider") STMActionsInfoProvider fileInfoProvider,
			@Qualifier("processEntityStore") EntityStore<Process> entityStore){
		return new ProcessManager(stm, fileInfoProvider, entityStore);
	}
	
	// Now we start constructing the STM Components 
	
	@Bean @Autowired
    ProcessEntryAction processEntryAction(@Qualifier("processEntityStore") EntityStore<Process> entityStore,
                                          @Qualifier("processActionsInfoProvider") STMActionsInfoProvider fileInfoProvider,
                                          @Qualifier("processFlowStore") STMFlowStoreImpl stmFlowStore){
        ProcessEntryAction entryAction =  new ProcessEntryAction(entityStore,fileInfoProvider);
        stmFlowStore.setEntryAction(entryAction);
        return entryAction;
	}
	
	@Bean GenericExitAction<Process> processExitAction(@Qualifier("processFlowStore") STMFlowStoreImpl stmFlowStore){
        GenericExitAction<Process> exitAction = new GenericExitAction<Process>();
        stmFlowStore.setExitAction(exitAction);
        return exitAction;
	}

	@Bean
	XmlFlowReader processFlowReader(@Qualifier("processFlowStore") STMFlowStoreImpl flowStore) throws Exception {
		XmlFlowReader flowReader = new XmlFlowReader(flowStore);
		flowReader.setFilename(FLOW_DEFINITION_FILE);
		return flowReader;
	}
	

	@Bean
    ProcessHealthChecker processHealthChecker(){
    	return new ProcessHealthChecker();
    }

    @Bean STMTransitionAction<Process> defaultfileSTMTransitionAction() {
        return new DefaultSTMTransitionAction<MinimalPayload>();
    }

    @Bean
    STMTransitionActionResolver processTransitionActionResolver(
    @Qualifier("defaultfileSTMTransitionAction") STMTransitionAction<Process> defaultSTMTransitionAction){
        return new STMTransitionActionResolver(PREFIX_FOR_RESOLVER,defaultSTMTransitionAction);
    }

    @Bean @Autowired StmBodyTypeSelector processBodyTypeSelector(
    @Qualifier("processActionsInfoProvider") STMActionsInfoProvider fileInfoProvider,
    @Qualifier("processTransitionActionResolver") STMTransitionActionResolver stmTransitionActionResolver) {
        return new StmBodyTypeSelector(fileInfoProvider,stmTransitionActionResolver);
    }

    @Bean @Autowired STMTransitionAction<Process> processBaseTransitionAction(
        @Qualifier("processTransitionActionResolver") STMTransitionActionResolver stmTransitionActionResolver,
        @Qualifier("processFlowStore") STMFlowStoreImpl stmFlowStore){
        BaseTransitionAction<Process> baseTransitionAction = new BaseTransitionAction<>(stmTransitionActionResolver);
        stmFlowStore.setDefaultTransitionAction(baseTransitionAction);
        return baseTransitionAction;
    }


    // Create the specific transition actions here. Make sure that these actions are inheriting from
    // AbstractSTMTransitionMachine (The sample classes provide an example of this). To automatically wire
    // them into the STM use the convention of "file" + eventId for the method name. (file is the
    // prefix passed to the TransitionActionResolver above.)
    // This will ensure that these are detected automatically by the Workflow system.
    // The payload types will be detected as well so that there is no need to introduce an <event-information/>
    // segment in src/main/resources/com/mycompany/file/process-states.xml
    
    @Bean
    SubProcessDoneSuccessfullyAction
            processSubProcessDoneSuccessfully(){
        return new SubProcessDoneSuccessfullyAction();
    }
    @Bean
    DoneSuccessfullyAction
    processDoneSuccessfully(){
        return new DoneSuccessfullyAction();
    }
    @Bean
    SplitDoneAction
            processSplitDone(){
        return new SplitDoneAction();
    }

    @Bean
    SplitDoneAction processSplitUpdate(){
        return new SplitDoneAction();
    }

    @Bean
    SubProcessDoneWithErrorsAction
            processSubProcessDoneWithErrors(){
        return new SubProcessDoneWithErrorsAction();
    }
    @Bean
    DoneWithErrorsAction
    processDoneWithErrors(){
        return new DoneWithErrorsAction();
    }
    @Bean
    StatusUpdateAction
    processStatusUpdate(){
        return new StatusUpdateAction();
    }

    @Bean ConfigProviderImpl processConfigProvider() {
        return new ConfigProviderImpl();
    }

    @Bean
    NotifyParent notifyParent(@Qualifier("_processStateEntityService_") StateEntityService<Process> stateEntityService){
        return new NotifyParent(stateEntityService);
    }

    @Bean ConfigBasedEnablementStrategy processConfigBasedEnablementStrategy(
        @Qualifier("processConfigProvider") ConfigProvider configProvider,
        @Qualifier("processFlowStore") STMFlowStoreImpl stmFlowStore) {
        ConfigBasedEnablementStrategy enablementStrategy = new ConfigBasedEnablementStrategy(configProvider,PREFIX_FOR_PROPERTIES);
        stmFlowStore.setEnablementStrategy(enablementStrategy);
        return enablementStrategy;
    }

    @Bean
    ProcessConfigurator processConfigurator() throws Exception{
       return new ProcessConfigurator();
    }

    @Bean
    PostSaveHook postSaveHook(){
        return new PostSaveHook();
    }

}
