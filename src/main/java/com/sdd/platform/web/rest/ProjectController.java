package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.governance.ProjectService;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.web.dto.ProjectDtos;
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

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    public ProjectDtos.ProjectPageDto list(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser AppUser caller
    ) {
        return ProjectDtos.ProjectPageDto.from(service.search(customerId, keyword, status, page, size, caller));
    }

    @GetMapping("/{id}")
    public ProjectDtos.ProjectDto get(@PathVariable UUID id, @CurrentUser AppUser caller) {
        return ProjectDtos.ProjectDto.from(service.get(id, caller));
    }

    @PostMapping
    public ResponseEntity<ProjectDtos.ProjectDto> create(
            @Valid @RequestBody ProjectDtos.CreateProjectRequest request,
            @CurrentUser AppUser caller
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectDtos.ProjectDto.from(service.create(
                request.customerId(),
                request.projectAlias(),
                request.projectType(),
                request.riskLevel(),
                request.teamIds(),
                caller
        )));
    }

    @PutMapping("/{id}")
    public ProjectDtos.ProjectDto update(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectDtos.UpdateProjectRequest request,
            @CurrentUser AppUser caller
    ) {
        return ProjectDtos.ProjectDto.from(service.update(
                id,
                request.customerId(),
                request.projectAlias(),
                request.projectType(),
                request.riskLevel(),
                request.teamIds(),
                caller
        ));
    }

    @PutMapping("/{id}/delete")
    public ProjectDtos.ProjectDto softDelete(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> ignoredBody,
            @CurrentUser AppUser caller
    ) {
        return ProjectDtos.ProjectDto.from(service.softDelete(id, caller));
    }
}
