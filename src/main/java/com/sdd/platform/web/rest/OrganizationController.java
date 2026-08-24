package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.governance.OrganizationService;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.web.dto.OrganizationDtos;
import com.sdd.platform.web.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService service;

    public OrganizationController(OrganizationService service) {
        this.service = service;
    }

    @GetMapping
    public OrganizationDtos.OrganizationPageDto list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser AppUser caller
    ) {
        return OrganizationDtos.OrganizationPageDto.from(service.search(keyword, status, page, size, caller));
    }

    @GetMapping("/{id}")
    public OrganizationDtos.OrganizationDto get(@PathVariable UUID id, @CurrentUser AppUser caller) {
        return OrganizationDtos.OrganizationDto.from(service.get(id, caller));
    }

    @PostMapping
    public ResponseEntity<OrganizationDtos.OrganizationDto> create(
            @Valid @RequestBody OrganizationDtos.CreateOrganizationRequest request,
            @CurrentUser AppUser caller
    ) {
        var created = service.create(
                request.organizationCode(),
                request.organizationName(),
                request.description(),
                caller
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(OrganizationDtos.OrganizationDto.from(created));
    }

    @PutMapping("/{id}")
    public OrganizationDtos.OrganizationDto update(
            @PathVariable UUID id,
            @Valid @RequestBody OrganizationDtos.UpdateOrganizationRequest request,
            @CurrentUser AppUser caller
    ) {
        return OrganizationDtos.OrganizationDto.from(service.update(
                id,
                request.organizationCode(),
                request.organizationName(),
                request.description(),
                request.status(),
                request.version(),
                caller
        ));
    }

    @PatchMapping("/{id}/delete")
    public OrganizationDtos.OrganizationDto softDelete(
            @PathVariable UUID id,
            @RequestBody OrganizationDtos.DeleteOrganizationRequest request,
            @CurrentUser AppUser caller
    ) {
        return OrganizationDtos.OrganizationDto.from(service.softDelete(id, request.version(), caller));
    }
}
