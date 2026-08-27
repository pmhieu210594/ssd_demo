package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.governance.RepositoryService;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.web.dto.RepositoryDtos;
import com.sdd.platform.web.security.CurrentUser;
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
@RequestMapping("/api/v1/repositories")
public class RepositoryController {

    private final RepositoryService service;

    public RepositoryController(RepositoryService service) {
        this.service = service;
    }

    @GetMapping
    public RepositoryDtos.RepositoryPageDto list(
        @RequestParam(required = false) UUID projectId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @CurrentUser AppUser caller
    ) {
        return RepositoryDtos.RepositoryPageDto.from(service.search(projectId, status, keyword, page, size, caller));
    }

    @GetMapping("/{id}")
    public RepositoryDtos.RepositoryDto get(@PathVariable UUID id, @CurrentUser AppUser caller) {
        return RepositoryDtos.RepositoryDto.from(service.get(id, caller));
    }

    @PostMapping
    public ResponseEntity<RepositoryDtos.RepositoryDto> create(
            @RequestBody RepositoryDtos.CreateRepositoryRequest request,
            @CurrentUser AppUser caller
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(RepositoryDtos.RepositoryDto.from(service.create(
                request.projectId(),
                request.repo_name_masked(),
                request.host_type(),
                request.default_branch(),
                request.repo_url_hash(),
                caller
        )));
    }

    @PutMapping("/{id}")
    public RepositoryDtos.RepositoryDto update(
            @PathVariable UUID id,
            @RequestBody RepositoryDtos.UpdateRepositoryRequest request,
            @CurrentUser AppUser caller
    ) {
        return RepositoryDtos.RepositoryDto.from(service.update(
                id,
                request.projectId(),
                request.repo_name_masked(),
                request.host_type(),
                request.default_branch(),
                request.repo_url_hash(),
                caller
        ));
    }

    @PutMapping("/{id}/delete")
    public RepositoryDtos.RepositoryDto softDelete(@PathVariable UUID id, @CurrentUser AppUser caller) {
        return RepositoryDtos.RepositoryDto.from(service.softDelete(id, caller));
    }
}
