package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.governance.RoleService;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.web.dto.RoleDtos;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @GetMapping
    public List<RoleDtos.RoleDto> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort,
            @CurrentUser AppUser caller
    ) {
        return service.list(keyword, sort, caller).stream()
                .map(RoleDtos.RoleDto::from)
                .toList();
    }

    @GetMapping("/{id}")
    public RoleDtos.RoleDto get(@PathVariable UUID id, @CurrentUser AppUser caller) {
        return RoleDtos.RoleDto.from(service.get(id, caller));
    }

    @PostMapping
    public ResponseEntity<RoleDtos.RoleDto> create(
            @RequestBody RoleDtos.CreateRoleRequest request,
            @CurrentUser AppUser caller
    ) {
        var created = service.create(request.roleName(), request.description(), caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(RoleDtos.RoleDto.from(created));
    }

    @PutMapping("/{id}")
    public RoleDtos.RoleDto update(
            @PathVariable UUID id,
            @RequestBody RoleDtos.UpdateRoleRequest request,
            @CurrentUser AppUser caller
    ) {
        return RoleDtos.RoleDto.from(service.update(id, request.roleName(), request.description(), caller));
    }

    @PutMapping("/{id}/delete")
    public RoleDtos.RoleDto logicalDelete(@PathVariable UUID id, @CurrentUser AppUser caller) {
        return RoleDtos.RoleDto.from(service.logicalDelete(id, caller));
    }
}
