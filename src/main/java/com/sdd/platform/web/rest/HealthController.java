package com.sdd.platform.web.rest;

import com.sdd.platform.web.dto.Dtos;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public Dtos.HealthDto health() {
        return new Dtos.HealthDto("UP", MDC.get("traceId"));
    }
}
