package org.chenile.orchestrator.process.configuration.controller;

import java.util.Map;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.configuration.management.ProcessManagementService;
import org.chenile.orchestrator.process.configuration.management.ProcessManagementService.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/process-management/api")
@ConditionalOnProperty(name = "chenile.process.management.enabled", havingValue = "true")
public class ProcessManagementController {
  private final ProcessManagementService service;

  public ProcessManagementController(ProcessManagementService service) {
    this.service = service;
  }

  @GetMapping("/info")
  public Object info() {
    return service.info();
  }

  @GetMapping("/processes")
  public Object processes(
      @RequestHeader("x-chenile-tenant-id") String tenant,
      @RequestParam(required = false) String processType,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String triggerId,
      @RequestParam(required = false) String parentId,
      @RequestParam(required = false) String predecessorId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    return service.processes(
        tenant, processType, status, triggerId, parentId, predecessorId, page, size);
  }

  @GetMapping("/processes/{id}")
  public Object process(
      @RequestHeader("x-chenile-tenant-id") String tenant, @PathVariable String id) {
    return service.process(tenant, id);
  }

  @GetMapping("/executions")
  public Object executions(
      @RequestHeader("x-chenile-tenant-id") String tenant,
      @RequestParam(required = false) String triggerId,
      @RequestParam(required = false) String scheduleId,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    return service.executions(tenant, triggerId, scheduleId, status, page, size);
  }

  @GetMapping("/schedules")
  public Object schedules(
      @RequestHeader("x-chenile-tenant-id") String tenant,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    return service.schedules(tenant, page, size);
  }

  @PostMapping("/schedules")
  @ResponseStatus(HttpStatus.CREATED)
  public Object addSchedule(
      @RequestHeader("x-chenile-tenant-id") String tenant, @RequestBody ScheduleRequest request) {
    return service.saveSchedule(tenant, null, request);
  }

  @PutMapping("/schedules/{id}")
  public Object updateSchedule(
      @RequestHeader("x-chenile-tenant-id") String tenant,
      @PathVariable String id,
      @RequestBody ScheduleRequest request) {
    return service.saveSchedule(tenant, id, request);
  }

  @PostMapping("/triggers")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Object generate(
      @RequestHeader("x-chenile-tenant-id") String tenant, @RequestBody ManualRequest request) {
    return service.generate(tenant, request);
  }

  @GetMapping("/definitions")
  public Object definitions() {
    return service.definitions();
  }

  @PostMapping("/definitions")
  @ResponseStatus(HttpStatus.CREATED)
  public Object addDefinition(@RequestBody ProcessDef definition) {
    return service.saveDefinition(definition.processType, definition, true);
  }

  @PutMapping("/definitions/{name}")
  public Object updateDefinition(@PathVariable String name, @RequestBody ProcessDef definition) {
    return service.saveDefinition(name, definition, false);
  }

  @PostMapping("/definitions/refresh")
  public void refreshDefinitions() {
    service.refreshDefinitions();
  }

  @GetMapping("/trace")
  public Object trace(
      @RequestHeader("x-chenile-tenant-id") String tenant,
      @RequestParam(required = false) String processId,
      @RequestParam(required = false) String triggerId) {
    return service.trace(tenant, processId, triggerId);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Object invalid(IllegalArgumentException e) {
    return Map.of("message", e.getMessage() == null ? "Invalid request" : e.getMessage());
  }

  // This existing Spring adapter also consumes services exposed through the Chenile highway.
  @ExceptionHandler(org.chenile.base.exception.ErrorNumException.class)
  public org.springframework.http.ResponseEntity<?> serviceError(
      org.chenile.base.exception.ErrorNumException e) {
    return org.springframework.http.ResponseEntity.status(e.getErrorNum())
        .body(Map.of("message", e.getMessage() == null ? "Request failed" : e.getMessage()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Object conflict() {
    return Map.of("message", "A record with this identifier or name already exists");
  }

  @ExceptionHandler({
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Object malformedRequest() {
    return Map.of("message", "Invalid request JSON, enum value or query parameter type");
  }

  @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
  public org.springframework.http.ResponseEntity<?> status(
      org.springframework.web.server.ResponseStatusException e) {
    return org.springframework.http.ResponseEntity.status(e.getStatusCode())
        .body(Map.of("message", e.getReason() == null ? "Request failed" : e.getReason()));
  }
}
