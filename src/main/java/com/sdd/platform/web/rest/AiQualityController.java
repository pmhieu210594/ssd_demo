package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.aiquality.AiQualityService;
import com.sdd.platform.domain.model.AuthUserContext;
import com.sdd.platform.web.dto.AiQualityDtos;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai-qualities")
public class AiQualityController {

    private final AiQualityService service;

    public AiQualityController(AiQualityService service) {
        this.service = service;
    }

    @GetMapping
    public AiQualityDtos.AiQualityPageDto list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) UUID ticketId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser AuthUserContext caller
    ) {
        return AiQualityDtos.AiQualityPageDto.from(
                service.search(projectId, repositoryId, ticketId, search, page, size, caller));
        }

    @GetMapping("/{id}")
    public AiQualityDtos.AiQualityDto get(@PathVariable UUID id, @CurrentUser AuthUserContext caller) {
        return AiQualityDtos.AiQualityDto.from(service.get(id, caller));
    }

    @GetMapping("/by-ticket/{ticketId}")
    public ResponseEntity<AiQualityDtos.AiQualityDto> byTicket(
            @PathVariable UUID ticketId,
            @CurrentUser AuthUserContext caller
    ) {
        return service.getByTicketId(ticketId, caller)
                .map(model -> ResponseEntity.ok(AiQualityDtos.AiQualityDto.from(model)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<AiQualityDtos.AiQualityDto> create(
            @Valid @RequestBody AiQualityDtos.CreateAiQualityRequest request,
            @CurrentUser AuthUserContext caller
    ) {
        var created = service.create(
                request.projectId(),
                request.repositoryId(),
                request.ticketId(),
                request.aiQualityRate(),
                caller
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(AiQualityDtos.AiQualityDto.from(created));
    }

    @PutMapping("/{id}")
    public AiQualityDtos.AiQualityDto update(
            @PathVariable UUID id,
            @Valid @RequestBody AiQualityDtos.UpdateAiQualityRequest request,
            @CurrentUser AuthUserContext caller
    ) {
        return AiQualityDtos.AiQualityDto.from(service.update(id, request.aiQualityRate(), caller));
    }

    @PutMapping("/{id}/delete")
    public AiQualityDtos.AiQualityDto softDelete(@PathVariable UUID id, @CurrentUser AuthUserContext caller) {
        return AiQualityDtos.AiQualityDto.from(service.softDelete(id, caller));
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
}
