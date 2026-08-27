package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.domain.model.RoleOption;
import com.sdd.platform.domain.model.UserAccountAdminView;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class UserAccountAdminDtos {
    private UserAccountAdminDtos() {}

    public record UserAccountDto(
            UUID accountId,
            UUID memberKey,
            String username,
            String fullname,
            String email,
            UUID roleId,
            String roleName,
            UUID teamId,
            String teamName,
            boolean isActive,
            OffsetDateTime lastLoginAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static UserAccountDto from(UserAccountAdminView account) {
            return new UserAccountDto(
                    account.getAccountId(),
                    account.getMemberKey(),
                    account.getUsername(),
                    account.getFullname(),
                    account.getEmail(),
                    account.getRoleId(),
                    account.getRoleName(),
                    account.getTeamId(),
                    account.getTeamName(),
                    account.isActive(),
                    account.getLastLoginAt(),
                    account.getCreatedAt(),
                    account.getUpdatedAt()
            );
        }
    }

    public record UserAccountPageDto(
            List<UserAccountDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static UserAccountPageDto from(PageResult<UserAccountAdminView> result) {
            return new UserAccountPageDto(
                    result.items().stream().map(UserAccountDto::from).toList(),
                    result.page(),
                    result.size(),
                    result.totalElements(),
                    result.totalPages()
            );
        }
    }

    public record RoleOptionDto(UUID roleId, String roleName) {
        public static RoleOptionDto from(RoleOption role) {
            return new RoleOptionDto(role.getRoleId(), role.getRoleName());
        }
    }

    public record CreateUserAccountRequest(
            String username,
            String fullname,
            String email,
            String password,
            String confirmPassword,
            UUID roleId,
            Boolean isActive
    ) {}

    public record UpdateUserAccountRequest(
            String fullname,
            String email,
            UUID roleId,
            Boolean isActive
    ) {}

    public record ResetPasswordRequest(String password, String confirmPassword) {}
}
