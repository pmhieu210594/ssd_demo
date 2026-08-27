package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.governance.UserAccountAdminService;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.web.dto.UserAccountAdminDtos;
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
@RequestMapping("/api/v1/admin")
public class UserAccountAdminController {

    private final UserAccountAdminService service;

    public UserAccountAdminController(UserAccountAdminService service) {
        this.service = service;
    }

    @GetMapping("/user-accounts")
    public UserAccountAdminDtos.UserAccountPageDto list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) List<UUID> roleIds,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser AppUser caller
    ) {
        return UserAccountAdminDtos.UserAccountPageDto.from(
                service.search(keyword, status, roleIds != null ? roleIds : roleId == null ? null : List.of(roleId), page, size, caller)
        );
    }

    @GetMapping("/user-accounts/{accountId}")
    public UserAccountAdminDtos.UserAccountDto get(@PathVariable UUID accountId, @CurrentUser AppUser caller) {
        return UserAccountAdminDtos.UserAccountDto.from(service.get(accountId, caller));
    }

    @PostMapping("/user-accounts")
    public ResponseEntity<UserAccountAdminDtos.UserAccountDto> create(
            @RequestBody UserAccountAdminDtos.CreateUserAccountRequest request,
            @CurrentUser AppUser caller
    ) {
        var created = service.create(
                request.username(),
                request.fullname(),
                request.email(),
                request.password(),
                request.confirmPassword(),
                request.roleId(),
                request.isActive(),
                caller
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(UserAccountAdminDtos.UserAccountDto.from(created));
    }

    @PutMapping("/user-accounts/{accountId}")
    public UserAccountAdminDtos.UserAccountDto update(
            @PathVariable UUID accountId,
            @RequestBody UserAccountAdminDtos.UpdateUserAccountRequest request,
            @CurrentUser AppUser caller
    ) {
        return UserAccountAdminDtos.UserAccountDto.from(service.update(
                accountId,
                request.fullname(),
                request.email(),
                request.roleId(),
                request.isActive(),
                caller
        ));
    }

    @PostMapping("/user-accounts/{accountId}/activate")
    public UserAccountAdminDtos.UserAccountDto activate(@PathVariable UUID accountId, @CurrentUser AppUser caller) {
        return UserAccountAdminDtos.UserAccountDto.from(service.activate(accountId, caller));
    }

    @PostMapping("/user-accounts/{accountId}/deactivate")
    public UserAccountAdminDtos.UserAccountDto deactivate(@PathVariable UUID accountId, @CurrentUser AppUser caller) {
        return UserAccountAdminDtos.UserAccountDto.from(service.deactivate(accountId, caller));
    }

    @PostMapping("/user-accounts/{accountId}/reset-password")
    public UserAccountAdminDtos.UserAccountDto resetPassword(
            @PathVariable UUID accountId,
            @RequestBody UserAccountAdminDtos.ResetPasswordRequest request,
            @CurrentUser AppUser caller
    ) {
        return UserAccountAdminDtos.UserAccountDto.from(
                service.resetPassword(accountId, request.password(), request.confirmPassword(), caller)
        );
    }

    @GetMapping("/roles")
    public List<UserAccountAdminDtos.RoleOptionDto> roles(@CurrentUser AppUser caller) {
        return service.roles(caller).stream().map(UserAccountAdminDtos.RoleOptionDto::from).toList();
    }
}
