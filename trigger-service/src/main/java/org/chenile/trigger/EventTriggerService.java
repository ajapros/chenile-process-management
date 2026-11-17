package org.chenile.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.chenile.base.exception.NotFoundException;
import org.chenile.core.context.ContextContainer;
import org.chenile.orchestrator.process.outbox.*;
import org.chenile.trigger.model.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Commits durable transactionTemplate before returning; execution is exclusively the dispatcher's
 * job.
 */
public class EventTriggerService implements TriggerService {
  private final ProcessOutboxRepository outbox;
  private final OutboxCommandProducer<OutboxCommand> producer;
  private final ObjectMapper mapper;
  private final TransactionTemplate transactionTemplate;
  private final TriggerRequests requests = new TriggerRequests();

  public EventTriggerService(
      ProcessOutboxRepository outbox,
      DispatchTriggerCommand command,
      ObjectMapper mapper,
      PlatformTransactionManager manager) {
    this.outbox = outbox;
    this.mapper = mapper;
    producer = new OutboxCommandProducer<>(List.of(command));
    transactionTemplate = new TransactionTemplate(manager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public TriggerResult trigger(TriggerInput input) {
    requests.validate(input);
    var headers = requests.headers(input);
    String tenant = requests.tenant(headers);
    String id = UUID.randomUUID().toString();
    var data =
        new Trigger(
            input.eventId,
            input.triggerTime == null ? Instant.now() : input.triggerTime,
            headers,
            input.triggerContext == null ? Map.of() : input.triggerContext,
            input.payload,
            input.source);
    OutboxCommand entry;
    try {
      String key =
          input.idempotencyKey == null || input.idempotencyKey.isBlank()
              ? "trigger:" + id
              : "trigger:"
                  + HexFormat.of()
                      .formatHex(
                          MessageDigest.getInstance("SHA-256")
                              .digest(
                                  mapper.writeValueAsBytes(
                                      List.of(tenant, input.eventId, input.idempotencyKey))));
      entry =
          OutboxCommand.create(
              null, DispatchTriggerCommand.TYPE, key, mapper.writeValueAsString(data));
      entry.id = id;
      entry.tenantId = tenant;
    } catch (Exception failure) {
      throw new IllegalArgumentException("Cannot persist trigger submission", failure);
    }
    String acceptedId =
        transactionTemplate.execute(
            tx -> {
              try {
                producer.enqueue(entry);
              } catch (Exception failure) {
                throw new IllegalStateException("Cannot enqueue trigger", failure);
              }
              return outbox.findByIdempotencyKey(entry.idempotencyKey).orElseThrow().id;
            });
    return new TriggerResult(acceptedId);
  }

  @Override
  public TriggerStatus status(String triggerId) {
    String tenant = ContextContainer.getInstance().getTenant();
    var command =
        outbox
            .findById(triggerId)
            .filter(
                c ->
                    DispatchTriggerCommand.TYPE.equals(c.commandType)
                        && Objects.equals(tenant, c.tenantId))
            .orElseThrow(
                () ->
                    new NotFoundException("TRIGGER_NOT_FOUND", "Trigger not found in this tenant"));
    return describe(command, mapper);
  }

  public static TriggerStatus describe(OutboxCommand command, ObjectMapper mapper) {
    try {
      var data = mapper.readValue(command.payload, Trigger.class);
      return new TriggerStatus(
          command.id,
          data.eventId(),
          command.status,
          command.attempt,
          data.triggerTime(),
          command.createdAt,
          command.startedAt,
          command.finishedAt,
          command.errorMessage,
          data.source(),
          data.triggerContext(),
          data.payload());
    } catch (Exception failure) {
      throw new IllegalStateException("Invalid trigger envelope " + command.id, failure);
    }
  }
}
