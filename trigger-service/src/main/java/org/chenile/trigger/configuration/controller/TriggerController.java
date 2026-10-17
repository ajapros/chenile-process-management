package org.chenile.trigger.configuration.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.chenile.base.response.GenericResponse;
import org.chenile.http.annotation.ChenileController;
import org.chenile.http.handler.ControllerSupport;
import org.chenile.trigger.model.TriggerInput;
import org.chenile.trigger.model.TriggerResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the same trigger dispatch and idempotency contract used by other adapters. */
@RestController
@ChenileController(value = "trigger", serviceName = "triggerService")
public class TriggerController extends ControllerSupport {
    @PostMapping("/trigger")
    public ResponseEntity<GenericResponse<TriggerResult>> trigger(
            HttpServletRequest request, @RequestBody TriggerInput input) {
        return process(request, input);
    }
}
