package org.chenile.orchestrator.process.configuration.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.chenile.base.response.GenericResponse;
import org.chenile.http.annotation.ChenileController;
import org.chenile.http.annotation.ChenileResponseCodes;
import org.chenile.http.handler.ControllerSupport;
import org.chenile.orchestrator.process.api.ProcessDefinitionService;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ChenileController(
    value = "processDefinitionService",
    serviceName = "_processDefinitionService_",
    interfaceClass = ProcessDefinitionService.class,
    bluePrintName = "chenile-service")
public class ProcessDefinitionController extends ControllerSupport {
  @PostMapping("/process-definitions")
  @ChenileResponseCodes(success = 201, warning = 201)
  public ResponseEntity<GenericResponse<ProcessDef>> create(
      HttpServletRequest request, @RequestBody ProcessDef definition) {
    return process(request, definition);
  }

  @PutMapping("/process-definitions/{processType}")
  public ResponseEntity<GenericResponse<ProcessDef>> update(
      HttpServletRequest request,
      @PathVariable("processType") String processType,
      @RequestBody ProcessDef definition) {
    return process(request, processType, definition);
  }

  @GetMapping("/process-definitions/{processType}")
  public ResponseEntity<GenericResponse<ProcessDef>> retrieve(
      HttpServletRequest request, @PathVariable("processType") String processType) {
    return process(request, processType);
  }

  @GetMapping("/process-definitions")
  public ResponseEntity<GenericResponse<List<ProcessDef>>> findAll(HttpServletRequest request) {
    return process(request);
  }
}
