package org.chenile.trigger.configuration.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.chenile.base.response.GenericResponse;
import org.chenile.http.annotation.ChenileController;
import org.chenile.http.handler.ControllerSupport;
import org.chenile.trigger.model.TriggerInput;
import org.chenile.trigger.model.TriggerResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.chenile.trigger.model.TriggerStatus;

import java.net.URI;

/** HTTP adapter for the same trigger dispatch and idempotency contract used by other adapters. */
@RestController
@ChenileController(value = "trigger", serviceName = "triggerService")
public class TriggerController extends ControllerSupport {
    @PostMapping("/trigger")
    public ResponseEntity<GenericResponse<TriggerResult>> trigger(
            HttpServletRequest request, @RequestBody TriggerInput input) {
        ResponseEntity<GenericResponse<TriggerResult>> response = process(request, input);
        if (!response.getStatusCode().is2xxSuccessful()) return response;
        response.getBody().setCode(202);
        return ResponseEntity.accepted().headers(response.getHeaders())
                .location(URI.create("/trigger/" + response.getBody().getData().triggerId()))
                .body(response.getBody());
    }
    @GetMapping("/trigger/{triggerId}")
    public ResponseEntity<GenericResponse<TriggerStatus>> status(HttpServletRequest request,
            @PathVariable("triggerId") String triggerId) {
        return process(request, triggerId);
    }
}
