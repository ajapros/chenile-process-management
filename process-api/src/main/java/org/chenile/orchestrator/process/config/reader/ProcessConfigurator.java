package org.chenile.orchestrator.process.config.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.chenile.base.exception.ConfigurationException;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.config.model.Processes;

/**
 * Reads additional configuration about a process based on its process type.<br>
 * This information can be used by other classes to make some choices. For example, the Workflow
 * service for Process uses this to default the leaf node attribute in the {@link
 * org.chenile.orchestrator.process.model.Process} model. <br>
 * This also provides additional information to start a worker.
 */
public class ProcessConfigurator extends ProcessConfiguratorBase {

  public void read(String file) {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(file)) {
      read(stream);
    } catch (IOException e) {
      throw new ConfigurationException(
          "1200", "File name " + file + " cannot be processed. " + " Error = " + e.getMessage());
    }
  }

  public void read(InputStream inputStream) throws IOException {
    read(inputStream, Processes.class);
  }

  /** Read a typed extension of the standard process document, retaining normal lookup behavior. */
  public <T extends Processes> T read(InputStream inputStream, Class<T> documentType)
      throws IOException {
    T document =
        new ObjectMapper()
            .readerFor(documentType)
            .without(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .readValue(inputStream);
    for (Map.Entry<String, ProcessDef> entry : document.processMap.entrySet()) {
      String key = entry.getKey();
      ProcessDef processDef = entry.getValue();
      processDef.processType = key;
    }
    validate(document);
    processes = document;
    return document;
  }

  /** Subclasses validate extended documents before replacing the active configuration. */
  protected void validate(Processes document) {}
}
