package org.chenile.configuration.crontab.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.chenile.base.response.GenericResponse;
import org.chenile.http.annotation.ChenileController;
import org.chenile.http.handler.ControllerSupport;
import org.chenile.trigger.cron.Crontab;
import org.chenile.trigger.cron.CrontabService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Chenile HTTP adapter; persistence, validation, and scheduling belong to the service. */
@RestController
@ChenileController(
    value = "crontabService",
    serviceName = "_crontabService_",
    interfaceClass = CrontabService.class,
    bluePrintName = "chenile-service")
public class CrontabController extends ControllerSupport {
  @PostMapping("/crontab")
  public ResponseEntity<GenericResponse<Crontab>> save(
      HttpServletRequest request, @RequestBody Crontab crontab) {
    return process(request, crontab);
  }

  @GetMapping("/crontab/{id}")
  public ResponseEntity<GenericResponse<Crontab>> retrieve(
      HttpServletRequest request, @PathVariable("id") String id) {
    return process(request, id);
  }

  @GetMapping("/crontab")
  public ResponseEntity<GenericResponse<List<Crontab>>> findAll(HttpServletRequest request) {
    return process(request);
  }

  @DeleteMapping("/crontab/{id}")
  public ResponseEntity<GenericResponse<Void>> delete(
      HttpServletRequest request, @PathVariable("id") String id) {
    return process(request, id);
  }
}
