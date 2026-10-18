package org.chenile.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.core.context.ContextContainer;
import org.chenile.core.context.HeaderUtils;
import org.chenile.orchestrator.process.outbox.*;
import org.chenile.trigger.model.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.chenile.base.exception.NotFoundException;
import org.chenile.base.exception.BadRequestException;
import java.time.Instant;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Commits durable acceptance before returning; execution is exclusively the dispatcher's job. */
public class EventTriggerService implements TriggerService {
    private final ProcessOutboxRepository outbox;
    private final OutboxCommandProducer<DispatchTriggerCommand.Submission> producer;
    private final ObjectMapper mapper;
    private final TransactionTemplate acceptance;
    public EventTriggerService(ProcessOutboxRepository outbox, DispatchTriggerCommand command,
            ObjectMapper mapper, PlatformTransactionManager manager) {
        this.outbox = outbox; this.mapper = mapper;
        producer = new OutboxCommandProducer<>(List.of(command));
        acceptance = new TransactionTemplate(manager);
        acceptance.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Override public TriggerResult trigger(TriggerInput input) {
        require(input != null, "Trigger input is required");
        require(input.eventId != null && !input.eventId.isBlank(), "Trigger eventId is required");
        var headers = new LinkedHashMap<String, String>();
        ContextContainer.getInstance().toMap().forEach((key, value) -> {
            if (key.startsWith("x-")) headers.put(key, value);
        });
        if (input.headers != null) input.headers.forEach((key, value) -> {
            require(key != null && key.startsWith("x-"), "Trigger headers must start with x-");
            headers.put(key, value);
        });
        String tenant = headers.get(HeaderUtils.TENANT_ID_KEY);
        String callerTenant = ContextContainer.getInstance().get(HeaderUtils.TENANT_ID_KEY);
        if (callerTenant != null && !callerTenant.isBlank()) {
            require(tenant == null || callerTenant.equals(tenant), "Trigger tenant must match caller context");
            tenant = callerTenant;
        }
        if (tenant == null) tenant = "";
        headers.put(HeaderUtils.TENANT_ID_KEY, tenant);
        // An incoming correlation header cannot choose or spoof the accepted trigger identity.
        headers.remove("x-chenile-trigger-id");
        String id = UUID.randomUUID().toString();
        var data = new DispatchTriggerCommand.Data(input.eventId,
                input.triggerTime == null ? Instant.now() : input.triggerTime, headers,
                input.triggerContext == null ? Map.of() : input.triggerContext, input.payload);
        OutboxCommand entry;
        try {
            String key = input.idempotencyKey == null || input.idempotencyKey.isBlank() ? "trigger:" + id
                    : "trigger:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                        mapper.writeValueAsBytes(List.of(tenant, input.eventId, input.idempotencyKey))));
            entry = OutboxCommand.create(null, DispatchTriggerCommand.TYPE, key, mapper.writeValueAsString(data));
            entry.id = id; entry.tenantId = tenant;
        } catch (Exception failure) { throw new IllegalArgumentException("Cannot persist trigger submission", failure); }
        String acceptedId = acceptance.execute(tx -> {
            try { producer.enqueue(new DispatchTriggerCommand.Submission(entry)); }
            catch (Exception failure) { throw new IllegalStateException("Cannot enqueue trigger", failure); }
            return outbox.findByIdempotencyKey(entry.idempotencyKey).orElseThrow().id;
        });
        return new TriggerResult(acceptedId);
    }
    @Override public TriggerStatus status(String triggerId) {
        String tenant = ContextContainer.getInstance().getTenant();
        var command = outbox.findById(triggerId).filter(c -> DispatchTriggerCommand.TYPE.equals(c.commandType)
                && Objects.equals(tenant, c.tenantId)).orElseThrow(() ->
                    new NotFoundException("TRIGGER_NOT_FOUND", "Trigger not found in this tenant"));
        return describe(command, mapper);
    }
    public static TriggerStatus describe(OutboxCommand command, ObjectMapper mapper) {
        try {
            var data = mapper.readValue(command.payload, DispatchTriggerCommand.Data.class);
            return new TriggerStatus(command.id, data.eventId(), command.status, command.attempt,
                    data.triggerTime(), command.createdAt, command.startedAt, command.finishedAt,
                    command.errorMessage, data.headers().getOrDefault("x-chenile-trigger-source", "EXTERNAL"),
                    data.triggerContext(), data.payload());
        } catch (Exception failure) { throw new IllegalStateException("Invalid trigger envelope " + command.id, failure); }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new BadRequestException("INVALID_TRIGGER", message);
    }
}
