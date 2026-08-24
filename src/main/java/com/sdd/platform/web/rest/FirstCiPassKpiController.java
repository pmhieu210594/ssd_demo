package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.governance.FirstCiPassKpiService;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.web.dto.FirstCiPassKpiDtos;
import com.sdd.platform.web.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kpi/first-ci-pass")
public class FirstCiPassKpiController {

    private final FirstCiPassKpiService service;

    public FirstCiPassKpiController(FirstCiPassKpiService service) {
        this.service = service;
    }

    @GetMapping("/ticket/{ticketId}")
    public ResponseEntity<FirstCiPassKpiDtos.FirstCiPassDto> forTicket(
            @PathVariable UUID ticketId,
            @CurrentUser AppUser caller) {
        return service.computeForTicket(ticketId, caller)
                .map(r -> ResponseEntity.ok(FirstCiPassKpiDtos.FirstCiPassDto.from(r)))
                .orElse(ResponseEntity.notFound().build());
    }
}
