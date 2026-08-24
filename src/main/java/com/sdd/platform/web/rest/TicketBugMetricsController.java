package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.ticketbugmetrics.TicketBugMetricsService;
import com.sdd.platform.domain.model.AuthUserContext;
import com.sdd.platform.web.dto.TicketBugMetricsDtos;
import com.sdd.platform.web.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ticket-bug-metrics")
public class TicketBugMetricsController {

    private final TicketBugMetricsService service;

    public TicketBugMetricsController(TicketBugMetricsService service) {
        this.service = service;
    }

    @GetMapping
    public TicketBugMetricsDtos.TicketBugMetricsPageDto list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser AuthUserContext caller
    ) {
        return TicketBugMetricsDtos.TicketBugMetricsPageDto.from(
                service.search(projectId, repositoryId, search, page, size, caller));
    }

    @GetMapping("/by-ticket/{ticketId}")
    public ResponseEntity<TicketBugMetricsDtos.TicketBugMetricsDto> byTicket(
            @PathVariable UUID ticketId,
            @CurrentUser AuthUserContext caller
    ) {
        return service.getByTicketId(ticketId, caller)
                .map(model -> ResponseEntity.ok(TicketBugMetricsDtos.TicketBugMetricsDto.from(model)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public TicketBugMetricsDtos.TicketBugMetricsDto get(@PathVariable UUID id, @CurrentUser AuthUserContext caller) {
        return TicketBugMetricsDtos.TicketBugMetricsDto.from(service.get(id, caller));
    }

    @PostMapping
    public ResponseEntity<TicketBugMetricsDtos.TicketBugMetricsDto> create(
            @Valid @RequestBody TicketBugMetricsDtos.CreateTicketBugMetricsRequest request,
            @CurrentUser AuthUserContext caller
    ) {
        var created = service.create(
                request.projectId(),
                request.repositoryId(),
                request.ticketId(),
                request.internalBugCount(),
                request.customerBugCount(),
                request.note(),
                caller
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketBugMetricsDtos.TicketBugMetricsDto.from(created));
    }

    @PutMapping("/{id}")
    public TicketBugMetricsDtos.TicketBugMetricsDto update(
            @PathVariable UUID id,
            @Valid @RequestBody TicketBugMetricsDtos.UpdateTicketBugMetricsRequest request,
            @CurrentUser AuthUserContext caller
    ) {
        return TicketBugMetricsDtos.TicketBugMetricsDto.from(service.update(
                id,
                request.internalBugCount(),
                request.customerBugCount(),
                request.note(),
                caller
        ));
    }

    @PutMapping("/{id}/delete")
    public TicketBugMetricsDtos.TicketBugMetricsDto softDelete(@PathVariable UUID id, @CurrentUser AuthUserContext caller) {
        return TicketBugMetricsDtos.TicketBugMetricsDto.from(service.softDelete(id, caller));
    }

    @GetMapping("/access")
    public ResponseEntity<Void> access(@RequestParam(required = false) UUID projectId, @CurrentUser AuthUserContext caller) {
        if (projectId != null) {
            service.requireAccess(projectId, caller);
        } else {
            service.requireAnyAccess(caller);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ticket-options")
    public List<TicketBugMetricsDtos.TicketOptionDto> ticketOptions(
            @RequestParam UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @CurrentUser AuthUserContext caller
    ) {
        return service.ticketOptions(projectId, repositoryId, caller).stream()
                .map(TicketBugMetricsDtos.TicketOptionDto::from)
                .toList();
    }

    @GetMapping("/options")
    public TicketBugMetricsDtos.TicketBugMetricsOptionsDto options(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @CurrentUser AuthUserContext caller
    ) {
        return TicketBugMetricsDtos.TicketBugMetricsOptionsDto.from(service.options(projectId, repositoryId, caller));
    }
}
