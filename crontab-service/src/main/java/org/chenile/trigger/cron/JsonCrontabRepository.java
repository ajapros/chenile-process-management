package org.chenile.trigger.cron;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** JSON-backed schedule repository. Loading is atomic; its snapshots are independent of callers. */
public class JsonCrontabRepository implements CrontabReader {
  private final ObjectMapper json =
      new ObjectMapper()
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
  private volatile Map<String, Crontab> schedules = Map.of();

  public void read(String resource) throws IOException {
    try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
      if (stream == null) throw new IOException("Crontab resource not found: " + resource);
      read(stream);
    }
  }

  public void read(InputStream stream) throws IOException {
    CrontabEntry[] entries = json.readValue(stream, CrontabEntry[].class);
    if (entries == null) throw new IllegalArgumentException("Crontabs must be a JSON array");
    var loaded = new LinkedHashMap<String, Crontab>();
    Set<String> names = new HashSet<>();
    for (CrontabEntry entry : entries) {
      if (entry == null) throw new IllegalArgumentException("Crontab cannot be null");
      if (entry.headers != null) {
        if (entry.headersJson != null)
          throw new IllegalArgumentException("Use headers or headersJson, not both");
        entry.headersJson = json.writeValueAsString(entry.headers);
      }
      if (entry.payload != null) {
        if (entry.eventPayloadJson != null)
          throw new IllegalArgumentException("Use payload or eventPayloadJson, not both");
        entry.eventPayloadJson = json.writeValueAsString(entry.payload);
      }
      CrontabValidator.validate(entry);
      if (!names.add(entry.name))
        throw new IllegalArgumentException("Duplicate cron name: " + entry.name);
      if (entry.id == null || entry.id.isBlank())
        entry.id =
            UUID.nameUUIDFromBytes(
                    (Objects.toString(entry.tenant, "") + ":" + entry.name)
                        .getBytes(StandardCharsets.UTF_8))
                .toString();
      Crontab cron = json.convertValue(entry, Crontab.class);
      if (loaded.putIfAbsent(cron.id, cron) != null)
        throw new IllegalArgumentException("Duplicate cron id: " + cron.id);
    }
    schedules = Collections.unmodifiableMap(loaded);
  }

  private Crontab copy(Crontab cron) {
    return json.convertValue(cron, Crontab.class);
  }

  @Override
  public List<Crontab> findAll() {
    return schedules.values().stream().map(this::copy).toList();
  }

  @Override
  public List<Crontab> findByEnabledTrue() {
    return schedules.values().stream().filter(c -> c.enabled).map(this::copy).toList();
  }

  @Override
  public Optional<Crontab> findById(String id) {
    return Optional.ofNullable(schedules.get(id)).map(this::copy);
  }
}
