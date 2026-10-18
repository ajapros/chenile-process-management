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

import org.chenile.http.annotation.ChenileResponseCodes;

/** HTTP adapter for the same trigger dispatch and idempotency contract used by other adapters. */
@RestController
@ChenileController(value = "trigger", serviceName = "triggerService")
public class TriggerController extends ControllerSupport {
    @PostMapping("/trigger")
    @ChenileResponseCodes(success = 202, warning = 202)
    public ResponseEntity<GenericResponse<TriggerResult>> trigger(
            HttpServletRequest request, @RequestBody TriggerInput input) {
        return process(request, input);
    }
    @GetMapping("/trigger/{triggerId}")
    public ResponseEntity<GenericResponse<TriggerStatus>> status(HttpServletRequest request,
            @PathVariable("triggerId") String triggerId) {
        return process(request, triggerId);
    }
}
