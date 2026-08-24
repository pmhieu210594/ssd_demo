package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.traceability.TraceabilityService;
import com.sdd.platform.web.dto.TraceabilityDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/traceability")
public class TraceabilityController {

    private final TraceabilityService service;

    public TraceabilityController(TraceabilityService service) {
        this.service = service;
    }

    @GetMapping("/{ticketId}")
    public TraceabilityDtos.TraceabilityResponseDto get(@PathVariable UUID ticketId) {
        return TraceabilityDtos.TraceabilityResponseDto.from(service.getTraceability(ticketId));
    }
}
