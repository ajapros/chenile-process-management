package org.chenile.orchestrator.process.service.defs;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.*;
import java.util.function.Function;
import org.chenile.base.exception.BadRequestException;
import org.chenile.base.exception.ErrorNumException;
import org.chenile.base.exception.NotFoundException;
import org.chenile.orchestrator.process.api.ProcessDefinitionService;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.config.reader.ProcessConfigurator;
import org.chenile.orchestrator.process.configuration.dao.ProcessDefinitionRepository;
import org.chenile.orchestrator.process.configuration.model.ProcessDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Persistence and validation shared by Chenile REST and the existing management API. */
public class ProcessDefinitionServiceImpl implements ProcessDefinitionService {
  private final ProcessConfigurator configurator;
  private final ProcessDefinitionRepository repository;
  private final ObjectMapper json = new ObjectMapper();
  @PersistenceContext private EntityManager entityManager;

  public ProcessDefinitionServiceImpl(
      ProcessConfigurator configurator, ProcessDefinitionRepository repository) {
    this.configurator = configurator;
    this.repository = repository;
  }

  @Override
  @Transactional
  public ProcessDef create(ProcessDef definition) {
    var database = writable();
    ProcessDef candidate = validate(definition == null ? null : definition.processType, definition);
    if (repository.existsById(candidate.processType)) throw conflict("Definition already exists");
    try {
      // Assigned identifiers make Spring Data save() merge; create must always INSERT.
      entityManager.persist(new ProcessDefinition(candidate.processType, encode(candidate)));
      entityManager.flush();
    } catch (org.hibernate.exception.ConstraintViolationException
        | org.springframework.dao.DataIntegrityViolationException duplicate) {
      throw conflict("Definition already exists");
    }
    invalidateAfterCommit(database);
    return candidate;
  }

  @Override
  @Transactional
  public ProcessDef update(String processType, ProcessDef definition) {
    var database = writable();
    ProcessDef candidate = validate(processType, definition);
    if (!repository.existsById(processType)) throw notFound(processType);
    repository.saveAndFlush(new ProcessDefinition(processType, encode(candidate)));
    invalidateAfterCommit(database);
    return candidate;
  }

  @Override
  @Transactional(readOnly = true)
  public ProcessDef retrieve(String processType) {
    if (configurator instanceof DatabaseProcessConfigurator) {
      return repository
          .findById(processType)
          .map(this::decode)
          .orElseThrow(() -> notFound(processType));
    }
    ProcessDef definition = configurator.findByName(processType);
    if (definition == null) throw notFound(processType);
    return copy(definition);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProcessDef> findAll() {
    Collection<ProcessDef> definitions =
        configurator instanceof DatabaseProcessConfigurator
            ? repository.findAll().stream().map(this::decode).toList()
            : configurator.processes.processMap.values();
    return definitions.stream()
        .sorted(Comparator.comparing(d -> d.processType))
        .map(this::copy)
        .toList();
  }

  private ProcessDef validate(String processType, ProcessDef definition) {
    if (definition == null
        || processType == null
        || processType.isBlank()
        || processType.length() > 255
        || definition.predecessorArgs == null
        || definition.config == null)
      throw bad("Process type, predecessor argument selection, and configuration are required");
    if (definition.processType != null
        && !definition.processType.isBlank()
        && !processType.equals(definition.processType))
      throw bad("Process type must match the URL");
    ProcessDef candidate = copy(definition);
    candidate.processType = processType;
    Map<String, ProcessDef> graph = new HashMap<>();
    findAll().forEach(d -> graph.put(d.processType, d));
    graph.put(processType, candidate);
    validateLink(graph, processType, d -> d.predecessorProcessType, "Predecessor chain");
    validateLink(graph, processType, d -> d.parentProcessType, "Parent hierarchy");
    return candidate;
  }

  private static void validateLink(
      Map<String, ProcessDef> graph, String name, Function<ProcessDef, String> link, String label) {
    Set<String> visited = new HashSet<>();
    for (String current = name; current != null && !current.isBlank(); ) {
      if (!visited.add(current)) throw bad(label + " must not contain a cycle");
      ProcessDef definition = graph.get(current);
      // Forward references allow a multi-level definition set to be entered incrementally.
      current = definition == null ? null : link.apply(definition);
    }
  }

  private DatabaseProcessConfigurator writable() {
    if (configurator instanceof DatabaseProcessConfigurator database) return database;
    throw conflict("JSON definitions are read-only; select the database configurator to edit");
  }

  private void invalidateAfterCommit(DatabaseProcessConfigurator database) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            database.clearCache();
          }
        });
  }

  private ProcessDef decode(ProcessDefinition row) {
    try {
      ProcessDef definition = json.readValue(row.definition, ProcessDef.class);
      definition.processType = row.processType;
      return definition;
    } catch (Exception failure) {
      throw new IllegalStateException("Invalid definition " + row.processType, failure);
    }
  }

  private String encode(ProcessDef definition) {
    try {
      return json.writeValueAsString(definition);
    } catch (Exception failure) {
      throw new IllegalStateException("Cannot serialize process definition", failure);
    }
  }

  private ProcessDef copy(ProcessDef definition) {
    return json.convertValue(definition, ProcessDef.class);
  }

  private static BadRequestException bad(String message) {
    return new BadRequestException("INVALID_PROCESS_DEFINITION", message);
  }

  private static ErrorNumException conflict(String message) {
    return new ErrorNumException(409, "PROCESS_DEFINITION_CONFLICT", message, null);
  }

  private static NotFoundException notFound(String name) {
    return new NotFoundException(
        "PROCESS_DEFINITION_NOT_FOUND", "Process definition not found: " + name);
  }
}
