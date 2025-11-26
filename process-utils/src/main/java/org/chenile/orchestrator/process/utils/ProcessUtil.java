package org.chenile.orchestrator.process.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.delegate.ProcessManagerClient;
import org.chenile.orchestrator.process.model.payload.StatusUpdatePayload;
import org.chenile.orchestrator.process.utils.api.IWorker;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.WorkerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

public abstract class ProcessUtil {
    private static final Logger logger = LoggerFactory.getLogger(ProcessUtil.class);
    public static String camelCase(WorkerType workerType){
        String s =  workerType.name().toLowerCase();
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }

    @SuppressWarnings("unchecked")
    public static <T> void invoke(IWorker<T> worker, WorkerDto workerDto) {
        TypeInfo<T> typeInfo = deriveType(worker,"doStart",1);
        if(typeInfo == null){
            logger.warn("Unable to invoke worker with value {}. Cannot derive type information.", workerDto.process.input);
            return;
        }
        try {
            T p;
            if (!typeInfo.isString)
                p = om.readValue(workerDto.process.input, typeInfo.type);
            else // skip the transformation if a string is expected
                p = (T)workerDto.process.input;
            worker.start(workerDto,p);
        }catch(Exception e){
            logger.warn("Unable to invoke worker of type {} (process type = {})  with args {}.",
                    workerDto.workerType,workerDto.process.processType,workerDto.process.input,e);
        }
    }

    private static final ObjectMapper om = new ObjectMapper();
    public static class TypeInfo<T> {
        public TypeReference<T> type;
        public boolean isString = false;
    }
    public static <T> TypeInfo<T> deriveType(Object worker,String methodName,int index) {
        try{
            TypeInfo<T> typeInfo = new TypeInfo<>();
            Method[] methods = worker.getClass().getDeclaredMethods();
            for (Method m: methods){
                if (!m.getName().equals(methodName))continue;
                if (m.isBridge()) continue;
                Type[] types = m.getGenericParameterTypes();
                if (types[index].getTypeName().equals(String.class.getTypeName()))
                    typeInfo.isString = true;
                typeInfo.type = new TypeReference<T>() {
                    @Override
                    public Type getType() {
                        return types[index];
                    }
                };
                return typeInfo;
            }

        }catch(Exception ignored){}
        return null;
    }

    public static void processProgressUpdate(ProcessManagerClient processManagerClient,String processId, int percent){
        StatusUpdatePayload update = new StatusUpdatePayload();
        update.percentComplete = percent;
        processManagerClient.statusUpdate(processId, update);
    }
}
