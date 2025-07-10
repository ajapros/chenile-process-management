package org.chenile.orchestrator.process.configuration.controller;

import org.chenile.orchestrator.process.model.Process;
import jakarta.servlet.http.HttpServletRequest;

import org.chenile.base.response.GenericResponse;
import org.chenile.http.annotation.BodyTypeSelector;
import org.chenile.http.annotation.ChenileController;
import org.chenile.http.annotation.ChenileParamType;
import org.chenile.http.handler.ControllerSupport;
import org.springframework.http.ResponseEntity;

import org.chenile.stm.StateEntity;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.chenile.workflow.dto.StateEntityServiceResponse;

@RestController
@ChenileController(value = "processService", serviceName = "_processStateEntityService_",
		healthCheckerName = "processHealthChecker")
public class ProcessController extends ControllerSupport{
	
	@GetMapping("/process/{id}")
	public ResponseEntity<GenericResponse<StateEntityServiceResponse<Process>>> retrieve(
			HttpServletRequest httpServletRequest,
			@PathVariable String id){
		return process(httpServletRequest,id);
	}

	@PostMapping("/process")
	public ResponseEntity<GenericResponse<StateEntityServiceResponse<Process>>> create(
			HttpServletRequest httpServletRequest,
			@ChenileParamType(StateEntity.class)
			@RequestBody Process entity){
		return process(httpServletRequest,entity);
	}

	
	@PatchMapping("/process/{id}/{eventID}")
	@BodyTypeSelector("processBodyTypeSelector")
	public ResponseEntity<GenericResponse<StateEntityServiceResponse<Process>>> processById(
			HttpServletRequest httpServletRequest,
			@PathVariable String id,
			@PathVariable String eventID,
			@ChenileParamType(Object.class) 
			@RequestBody String eventPayload){
		return process(httpServletRequest,id,eventID,eventPayload);
	}


}
