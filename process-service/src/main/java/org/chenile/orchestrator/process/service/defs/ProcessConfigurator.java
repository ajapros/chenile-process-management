package org.chenile.orchestrator.process.service.defs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.config.model.Processes;
import org.chenile.base.exception.ConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Reads additional configuration about a process based on its process type.<br/>
 * This information can be used by other classes to make some choices. For example, the
 * Workflow service for Process uses this to default the leaf node attribute in the {@link org.chenile.orchestrator.process.model.Process}
 * model. <br/>
 * This also provides additional information to start a worker.
 */
public class ProcessConfigurator {
    public Processes processes = new Processes();

    public void read(String file){
        try(InputStream stream = getClass().getClassLoader().getResourceAsStream(file)){
            processes = new ObjectMapper().readValue(stream, Processes.class);
            for (Map.Entry<String,ProcessDef> entry: processes.processMap.entrySet()){
                String key = entry.getKey();
                ProcessDef processDef = entry.getValue();
                processDef.processType = key;
            }
        }catch(IOException e){
            throw new ConfigurationException(1200,"File name " + file + " cannot be processed. "+
                    " Error = " + e.getMessage());
        }
    }
}
