package org.chenile.orchestrator.process.utils.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.chenile.orchestrator.delegate.ProcessManagerClient;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.utils.api.BatchService;
import org.chenile.orchestrator.process.utils.api.IWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.chenile.orchestrator.process.utils.ProcessUtil.camelCase;
import static org.chenile.orchestrator.process.utils.ProcessUtil.invoke;


public class BatchServiceBase<T> implements BatchService<T> {
    private static final Logger logger = LoggerFactory.getLogger(BatchServiceBase.class);
	private final ObjectMapper objectMapper = new ObjectMapper();
	@Autowired ProcessManagerClient processManagerClient ;
	@Autowired ApplicationContext applicationContext;
	public BatchServiceBase(){
		objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS,false);
	}

	@Override
	public Process doFirstTrigger(String firstProcessType,T input) {
        logger.debug("At the trigger method ");
		// Start the ingestion process.
		Process process = new Process();
		// Get the process started with the root process.
		process.processType = firstProcessType;
		try {
			process.input = objectMapper.writeValueAsString(input);
        }catch(Exception e){
			logger.warn("Cannot serialize object {} into string", input,e);
		}
		return processManagerClient.start(process);
	}

	@Override
	public boolean startWorker(WorkerDto workerDto) {
		String componentName = workerDto.process.processType + camelCase(workerDto.workerType);
        logger.info("Start() method of IngestionService. Worker Type is {}", workerDto.workerType);
		try {
			IWorker<?> actualWorker = (IWorker<?>) applicationContext.getBean(componentName);
			invoke(actualWorker,workerDto);
			return true;
		}catch(Exception e){
			logger.error("Cannot start the worker of type {}.",workerDto.workerType,e);
			return false;
		}
	}
}