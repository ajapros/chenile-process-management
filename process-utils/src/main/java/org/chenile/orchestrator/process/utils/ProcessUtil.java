package org.chenile.orchestrator.process.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.chenile.orchestrator.delegate.ProcessManagerClient;
import org.chenile.orchestrator.process.model.payload.StatusUpdatePayload;
import org.chenile.orchestrator.process.utils.api.IWorker;
import org.chenile.orchestrator.process.model.WorkerDto;
import org.chenile.orchestrator.process.model.WorkerType;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

public abstract class ProcessUtil {
    public static String camelCase(WorkerType workerType){
        String s =  workerType.name().toLowerCase();
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }

    @SuppressWarnings("unchecked")
    public static <T> void invoke(IWorker<T> worker, WorkerDto workerDto) {
        TypeInfo<T> typeInfo = deriveType(worker,"doStart",1);
        if(typeInfo == null){
            throw new IllegalStateException("Unable to invoke worker with value " + workerDto.process.input
                    + ". Cannot derive type information.");
        }
        try {
            T p;
            if (!typeInfo.isString)
                p = om.readValue(workerDto.process.input, typeInfo.type);
            else // skip the transformation if a string is expected
                p = (T)workerDto.process.input;
            worker.start(workerDto,p);
        }catch(Exception e){
            throw new IllegalStateException("Unable to invoke worker of type " + workerDto.workerType
                    + " (process type = " + workerDto.process.processType + ")", e);
        }
    }

    private static final JsonMapper om = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
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
