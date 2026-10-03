package org.chenile.orchestrator.process.configuration.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.config.reader.ProcessConfigurator;
import org.chenile.orchestrator.process.configuration.dao.*;
import org.chenile.orchestrator.process.configuration.model.*;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.ProcessDto;
import org.chenile.orchestrator.process.service.defs.DatabaseProcessConfigurator;
import org.chenile.trigger.TriggerService;
import org.chenile.trigger.cron.*;
import org.chenile.trigger.model.*;
import org.chenile.trigger.store.TriggerExecutionRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

/** Administration queries: definitions are global; execution data is always tenant-scoped. */
public class ProcessManagementService {
    private final ProcessRepository processes;
    private final CompletionEventRepository completions;
    private final ProcessDefinitionRepository definitions;
    private final ProcessConfigurator configurator;
    private final CrontabRepository crontabs;
    private final ObjectProvider<CrontabService> cronServices;
    private final TriggerExecutionRepository executions;
    private final TriggerService triggers;
    private final ObjectMapper json = new ObjectMapper();
    public ProcessManagementService(ProcessRepository processes, CompletionEventRepository completions,
            ProcessDefinitionRepository definitions, ProcessConfigurator configurator,
            CrontabRepository crontabs, ObjectProvider<CrontabService> cronServices,
            TriggerExecutionRepository executions, TriggerService triggers) {
        this.processes = processes; this.completions = completions; this.definitions = definitions;
        this.configurator = configurator; this.crontabs = crontabs; this.cronServices = cronServices;
        this.executions = executions; this.triggers = triggers;
    }

    public record ProcessView(String id, String processType, String status, boolean leaf,
            int completedPercent, String parentId, String predecessorId, String triggerId,
            String description, Instant createdAt, Instant stateEnteredAt, String input, String output,
            int numSubProcesses, int numCompletedSubProcesses, Object errors) {
        static ProcessView of(Process p) {
            return new ProcessView(p.id, p.processType,
                p.getCurrentState() == null ? null : p.getCurrentState().getStateId(), p.leaf,
                p.completedPercent, p.parentId, p.predecessorId, p.triggerId, p.description,
                p.createdTime == null ? null : p.createdTime.toInstant(),
                p.getStateEntryTime() == null ? null : p.getStateEntryTime().toInstant(), p.input, p.output,
                p.numSubProcesses, p.numCompletedSubProcesses, p.errors);
        }
    }
    public record PageResult<T>(List<T> content, long totalElements, int page, int size, int totalPages) {
        static <T> PageResult<T> of(Page<T> p) {
            return new PageResult<>(p.getContent(), p.getTotalElements(), p.getNumber(), p.getSize(), p.getTotalPages());
        }
    }
    public record ProcessDetail(ProcessView process, List<CompletionEventRecord> completionEvents) {}
    public record Node(String id, String kind, String label, String status, String time, Object data) {}
    public record Edge(String source, String target, String kind) {}
    public record Trace(List<Node> nodes, List<Edge> edges) {}
    public record CronRequest(String name, String cronExpression, String timezone, boolean enabled, ProcessDto payload) {}
    public record ManualRequest(String triggerId, ProcessDto payload) {}

    public Map<String, Object> info() {
        return Map.of("definitionSource", configurator instanceof DatabaseProcessConfigurator ? "database" : "json",
                "definitionsWritable", configurator instanceof DatabaseProcessConfigurator,
                "cronAvailable", cronServices.getIfAvailable() != null, "supportedEvents", List.of("ProcessCreate"));
    }
    public PageResult<ProcessView> processes(String tenant, String type, String status, String triggerId,
            String parentId, String predecessorId, int page, int size) {
        Specification<Process> query = tenant(tenant);
        query = filter(query, "processType", type);
        query = filter(query, "triggerId", triggerId);
        query = filter(query, "parentId", parentId);
        query = filter(query, "predecessorId", predecessorId);
        if (present(status)) query = query.and((root, q, cb) -> cb.equal(root.get("state").get("stateId"), status));
        return PageResult.of(processes.findAll(query, page(page, size, "createdTime")).map(ProcessView::of));
    }
    public ProcessDetail process(String tenant, String id) {
        Process p = processes.findOne(ProcessManagementService.<Process>tenant(tenant)
                .and((r,q,c) -> c.equal(r.get("id"), id))).orElseThrow(ProcessManagementService::notFound);
        return new ProcessDetail(ProcessView.of(p), completions.findByTenantAndProcessIdIn(tenant, List.of(id)));
    }
    public PageResult<TriggerExecution> executions(String tenant, String triggerId, String crontabId,
            String status, int page, int size) {
        Specification<TriggerExecution> query = filter(tenant(tenant), "triggerId", triggerId);
        query = filter(query, "crontabId", crontabId);
        query = filter(query, "status", status);
        return PageResult.of(executions.findAll(query, page(page, size, "startedAt")));
    }
    public PageResult<Crontab> crontabs(String tenant, int page, int size) {
        return PageResult.of(crontabs.findAll(tenant(tenant), page(page, size, "createdTime")));
    }
    public Crontab saveCron(String tenant, String id, CronRequest request) {
        if (request == null || !present(request.name()) || request.name().length() > 255 || !present(request.cronExpression()) || !present(request.timezone()))
            throw bad("Name, Quartz cron expression and timezone are required");
        validatePayload(request.payload());
        try { java.time.ZoneId.of(request.timezone()); }
        catch (java.time.DateTimeException invalidZone) { throw bad("Invalid timezone"); }
        Crontab cron = id == null ? new Crontab() : crontabs.findOne(ProcessManagementService.<Crontab>tenant(tenant)
                .and((r,q,c) -> c.equal(r.get("id"), id))).orElseThrow(ProcessManagementService::notFound);
        cron.name = request.name(); cron.cronExpression = request.cronExpression(); cron.timezone = request.timezone();
        cron.enabled = request.enabled(); cron.eventName = "ProcessCreate"; cron.tenant = tenant;
        cron.eventPayloadJson = encode(request.payload());
        cron.headersJson = encode(Map.of("x-chenile-tenant-id", tenant));
        CrontabService service = cronServices.getIfAvailable();
        if (service == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Quartz scheduler is not configured");
        return service.save(cron);
    }
    public TriggerResult generate(String tenant, ManualRequest request) {
        if (request == null) throw bad("Payload is required");
        if (present(request.triggerId()) && request.triggerId().length() > 80) throw bad("Manual trigger key must be at most 80 characters");
        validatePayload(request.payload());
        TriggerInput input = new TriggerInput();
        // Namespace caller keys by tenant: trigger idempotency is globally unique.
        input.triggerId = "manual:" + tenant + ":" + (present(request.triggerId()) ? request.triggerId() : UUID.randomUUID());
        input.eventId = "ProcessCreate"; input.payload = request.payload();
        input.headers.put("x-chenile-tenant-id", tenant);
        input.headers.put("chenile-trigger-source", "MANUAL");
        return triggers.trigger(input);
    }
    private void validatePayload(ProcessDto payload) {
        if (payload == null || !present(payload.processDefName) || configurator.findByName(payload.processDefName) == null)
            throw bad("A known processDefName is required");
        // Correlation is assigned by the trigger service, never accepted from caller payload.
        payload.triggerId = null;
    }
    public List<ProcessDef> definitions() {
        if (!(configurator instanceof DatabaseProcessConfigurator)) return configurator.processes.processMap.values()
                .stream().sorted(Comparator.comparing(d -> d.processType)).toList();
        return definitions.findAll(Sort.by("processType")).stream().map(row -> {
            try { ProcessDef d = json.readValue(row.definition, ProcessDef.class); d.processType = row.processType; return d; }
            catch (Exception failure) { throw new IllegalStateException("Invalid definition " + row.processType, failure); }
        }).toList();
    }
    @Transactional
    public ProcessDef saveDefinition(String name, ProcessDef definition, boolean create) {
        if (!(configurator instanceof DatabaseProcessConfigurator database))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "JSON definitions are read-only; select the database configurator to edit");
        if (definition == null || !present(name) || name.length() > 255 || definition.predecessorArgs == null)
            throw bad("Process type and predecessor argument selection are required");
        if (present(definition.processType) && !name.equals(definition.processType)) throw bad("Process type must match the URL");
        if (name.equals(definition.predecessorProcessType)) throw bad("A process cannot be its own predecessor");
        var current = definitions();
        Map<String, String> predecessors = new HashMap<>();
        current.forEach(d -> predecessors.put(d.processType, d.predecessorProcessType));
        predecessors.put(name, definition.predecessorProcessType);
        Set<String> visited = new HashSet<>();
        for (String type = name; type != null; type = predecessors.get(type))
            if (!visited.add(type)) throw bad("Predecessor chain must not contain a cycle");
        boolean exists = definitions.existsById(name);
        if (create && exists) throw new ResponseStatusException(HttpStatus.CONFLICT, "Definition already exists");
        if (!create && !exists) throw notFound();
        definition.processType = name;
        definitions.saveAndFlush(new ProcessDefinition(name, encode(definition)));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { database.clearCache(); }
        });
        return definition;
    }
    public void refreshDefinitions() {
        if (configurator instanceof DatabaseProcessConfigurator database) database.clearCache();
    }

    /** Bounded connected-component traversal: not just a trigger-id search (legacy rows may lack it). */
    public Trace trace(String tenant, String processId, String triggerId) {
        if (present(processId) == present(triggerId)) throw bad("Supply exactly one processId or triggerId");
        LinkedHashMap<String, Process> found = new LinkedHashMap<>();
        Set<String> triggerIds = new HashSet<>();
        Set<String> frontier = new HashSet<>();
        if (present(processId)) {
            var seed = processes.findOne(ProcessManagementService.<Process>tenant(tenant)
                .and((r,q,c) -> c.equal(r.get("id"), processId))).orElseThrow(ProcessManagementService::notFound);
            found.put(seed.id, seed); frontier.add(seed.id);
            if (present(seed.triggerId)) triggerIds.add(seed.triggerId);
        } else triggerIds.add(triggerId);
        do {
            Set<String> neighbors = new HashSet<>(frontier);
            for (String id : frontier) {
                var p = found.get(id);
                if (p != null) { if (present(p.parentId)) neighbors.add(p.parentId); if (present(p.predecessorId)) neighbors.add(p.predecessorId); }
            }
            Set<String> correlations = new HashSet<>(triggerIds);
            var rows = processes.findAll(ProcessManagementService.<Process>tenant(tenant).and((r,q,c) -> c.or(
                r.get("id").in(neighbors), r.get("parentId").in(neighbors), r.get("predecessorId").in(neighbors),
                r.get("triggerId").in(correlations))), PageRequest.of(0, 1001, Sort.by("id"))).getContent();
            frontier = new HashSet<>();
            for (var p : rows) if (!found.containsKey(p.id)) {
                found.put(p.id, p); frontier.add(p.id);
                if (present(p.triggerId)) triggerIds.add(p.triggerId);
            }
            if (found.size() > 1000 || rows.size() > 1000)
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Execution exceeds 1000 processes; use paginated process queries to inspect branches");
        } while (!frontier.isEmpty());
        List<Node> nodes = new ArrayList<>(); List<Edge> edges = new ArrayList<>();
        var histories = executions.findAll(ProcessManagementService.<TriggerExecution>tenant(tenant)
            .and((r,q,c) -> r.get("triggerId").in(triggerIds)), PageRequest.of(0, 1001)).getContent();
        if (histories.size() > 1000) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Too many trigger events for one trace");
        Map<String, String> triggerNodes = new HashMap<>();
        for (var e : histories) {
            String id = "trigger:" + e.id;
            if ("ProcessCreate".equals(e.eventName)) triggerNodes.put(e.triggerId, id);
            nodes.add(new Node(id, "TRIGGER", e.eventName + " · " + e.source, e.status, e.startedAt.toString(), e));
        }
        for (var p : found.values()) {
            String id = "process:" + p.id; var view = ProcessView.of(p);
            nodes.add(new Node(id, "PROCESS", p.processType, view.status(), view.createdAt() == null ? null : view.createdAt().toString(), view));
            if (found.containsKey(p.parentId)) edges.add(new Edge("process:" + p.parentId, id, "SUBPROCESS"));
            if (!present(p.parentId) && !present(p.predecessorId) && triggerNodes.containsKey(p.triggerId))
                edges.add(new Edge(triggerNodes.get(p.triggerId), id, "PROCESS_CREATE"));
        }
        var events = found.isEmpty() ? List.<CompletionEventRecord>of() : completions.findByTenantAndProcessIdIn(tenant, found.keySet());
        Set<String> eventProcesses = new HashSet<>();
        for (var e : events) {
            eventProcesses.add(e.processId);
            nodes.add(new Node("completion:" + e.processId, "COMPLETION", "ProcessCompleted", "GENERATED", e.generatedAt, e));
            edges.add(new Edge("process:" + e.processId, "completion:" + e.processId, "EMITS"));
        }
        for (var p : found.values()) if (found.containsKey(p.predecessorId))
            edges.add(new Edge((eventProcesses.contains(p.predecessorId) ? "completion:" : "process:") + p.predecessorId,
                    "process:" + p.id, eventProcesses.contains(p.predecessorId) ? "SUCCESSOR" : "LEGACY_SUCCESSOR"));
        if (nodes.isEmpty()) throw notFound();
        return new Trace(nodes, edges);
    }
    static <T> Specification<T> tenant(String tenant) { return (r,q,c) -> c.equal(r.get("tenant"), tenant); }
    static <T> Specification<T> filter(Specification<T> query, String field, String value) {
        return present(value) ? query.and((r,q,c) -> c.equal(r.get(field), value)) : query;
    }
    static Pageable page(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) throw bad("Page must be nonnegative; size must be 1–100");
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc(sort), Sort.Order.desc("id")));
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception e) { throw bad("Invalid JSON payload"); }
    }
    static boolean present(String s) { return s != null && !s.isBlank(); }
    static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found in this tenant"); }
}
