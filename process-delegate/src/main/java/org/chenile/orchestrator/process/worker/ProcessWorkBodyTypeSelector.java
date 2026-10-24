package org.chenile.orchestrator.process.worker;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import org.chenile.core.context.ChenileExchange;
import org.chenile.orchestrator.process.workers.WorkListenerInput;
import org.chenile.orchestrator.process.workers.WorkListenerService;
import org.chenile.owiz.Command;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ResolvableType;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

/** Resolves WorkListenerInput's payload type from the selected concrete listener class. */
@Component("processWorkBodyTypeSelector")
public class ProcessWorkBodyTypeSelector implements Command<ChenileExchange> {
  @Autowired private ApplicationContext applicationContext;

  @Override
  public void execute(ChenileExchange exchange) {
    String beanName = WorkerListenerNames.beanName(exchange);
    Object listener = applicationContext.getBean(beanName);
    Type acceptInputType = acceptMethod().getGenericParameterTypes()[2];
    if (!(acceptInputType instanceof ParameterizedType parameterizedInput)
        || parameterizedInput.getRawType() != WorkListenerInput.class) {
      throw new IllegalStateException(
          "WorkListenerService.accept must receive WorkListenerInput<T>");
    }
    ResolvableType listenerType =
        ResolvableType.forClass(listener.getClass()).as(WorkListenerService.class);
    ResolvableType payloadType = listenerType.getGeneric(0);
    Class<?> payloadClass = payloadType.resolve();
    if (payloadClass == null || payloadClass == Object.class) {
      throw new IllegalStateException(
          "Cannot resolve the payload type for WorkListener bean " + beanName);
    }

    Type candidatePayloadType = payloadType.getType();
    final Type resolvedPayloadType =
        candidatePayloadType instanceof TypeVariable<?> ? payloadClass : candidatePayloadType;
    Type resolvedInputType =
        new ParameterizedType() {
          @Override
          public Type[] getActualTypeArguments() {
            return new Type[] {resolvedPayloadType};
          }

          @Override
          public Type getRawType() {
            return parameterizedInput.getRawType();
          }

          @Override
          public Type getOwnerType() {
            return parameterizedInput.getOwnerType();
          }
        };
    exchange.setBodyType(
        new TypeReference<Object>() {
          @Override
          public Type getType() {
            return resolvedInputType;
          }
        });
  }

  private Method acceptMethod() {
    try {
      return WorkListenerService.class.getMethod(
          "accept", String.class, String.class, WorkListenerInput.class);
    } catch (NoSuchMethodException exception) {
      throw new IllegalStateException("WorkListenerService.accept method is missing", exception);
    }
  }
}
