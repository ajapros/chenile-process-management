package org.chenile.orchestrator.process.utils.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.chenile.orchestrator.delegate.ProcessManagerClient;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.WorkerType;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.utils.ErrorsHelper;
import org.chenile.orchestrator.process.utils.api.BatchService;
import org.chenile.orchestrator.process.utils.api.IWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.chenile.orchestrator.process.utils.ProcessUtil.camelCase;
import static org.chenile.orchestrator.process.utils.ProcessUtil.invoke;


public abstract class BatchServiceBase<T> implements BatchService<T> {
    private static final Logger logger = LoggerFactory.getLogger(BatchServiceBase.class);
	private final ObjectMapper objectMapper = new ObjectMapper();
	@Autowired ProcessManagerClient processManagerClient ;
	@Autowired ApplicationContext applicationContext;
	public BatchServiceBase(){
		objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS,false);
	}

	protected abstract String getClientName();

	@Override
	public Process doFirstTrigger(String firstProcessType,T input) {
        logger.debug("At the trigger method ");
		// Start the ingestion process.
		Process process = new Process();
		process.clientId = getClientName();
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
        logger.info("Start() method of BatchServiceBase. Worker Type is {}", workerDto.workerType);
		try {
			IWorker<?> actualWorker = (IWorker<?>) applicationContext.getBean(componentName);
			invoke(actualWorker,workerDto);
			return true;
		}catch(Exception e){
			logger.error("Cannot start the worker of type {}. Reporting the failure to the process daemon.",
					workerDto.workerType, e);
			try {
				ErrorsHelper.handleErrors(workerDto, e, processManagerClient, errorEvent(workerDto.workerType));
				return true;
			} catch (Exception reportingFailure) {
				logger.error("Unable to report worker failure for process {} to the process daemon.",
						workerDto.process.getId(), reportingFailure);
				return false;
			}
		}
	}

	private String errorEvent(WorkerType workerType) {
		return switch (workerType) {
			case SPLITTER -> Constants.Events.SPLIT_DONE_WITH_ERRORS;
			case AGGREGATOR -> Constants.Events.AGGREGATION_DONE_WITH_ERRORS;
			case EXECUTOR -> Constants.Events.DONE_WITH_ERRORS;
		};
	}
}
