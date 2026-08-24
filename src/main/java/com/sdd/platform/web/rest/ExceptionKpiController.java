package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.governance.ExceptionKpiService;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.web.dto.ExceptionKpiDtos;
import com.sdd.platform.web.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kpi/exceptions")
public class ExceptionKpiController {

    private final ExceptionKpiService service;

    public ExceptionKpiController(ExceptionKpiService service) {
        this.service = service;
    }

    @GetMapping("/ticket/{ticketId}")
    public ExceptionKpiDtos.ExceptionKpiDto forTicket(
            @PathVariable UUID ticketId,
            @CurrentUser AppUser caller) {
        return ExceptionKpiDtos.ExceptionKpiDto.from(service.computeForTicket(ticketId, caller));
    }
}
