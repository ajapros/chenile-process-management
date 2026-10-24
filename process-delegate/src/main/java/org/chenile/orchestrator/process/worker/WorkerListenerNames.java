package org.chenile.orchestrator.process.worker;

import java.util.Locale;
import org.chenile.base.exception.ConfigurationException;
import org.chenile.orchestrator.process.model.WorkerType;
import org.chenile.orchestrator.process.workers.WorkerConstants;
import org.chenile.core.context.ChenileExchange;

final class WorkerListenerNames {
  private WorkerListenerNames() {}

  static String beanName(String processType, String workerType) {
    if (processType == null || processType.isBlank()) {
      throw new ConfigurationException("NO_PROCESS_TYPE", "Process type header is required");
    }
    WorkerType type = parseWorkerType(workerType);
    return processType + camelCase(type);
  }

  static String beanName(ChenileExchange exchange) {
    return beanName(
        header(exchange, WorkerConstants.PROCESS_TYPE),
        header(exchange, WorkerConstants.WORKER_TYPE));
  }

  static WorkerType parseWorkerType(String workerType) {
    if (workerType == null || workerType.isBlank()) {
      throw new ConfigurationException("NO_WORKER_TYPE", "Worker type header is required");
    }
    try {
      return WorkerType.valueOf(workerType.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ConfigurationException(
          "INVALID_WORKER_TYPE", "Unsupported process worker type: " + workerType);
    }
  }

  static String camelCase(WorkerType workerType) {
    String value = workerType.name().toLowerCase(Locale.ROOT);
    return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
  }

  private static String header(ChenileExchange exchange, String name) {
    Object value = exchange.getHeader(name);
    return value == null ? null : value.toString();
  }
}
