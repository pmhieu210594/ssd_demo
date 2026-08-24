package com.sdd.platform.web.dto;

import com.sdd.platform.domain.model.Role;
import com.sdd.platform.web.validation.NoXssFields;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class RoleDtos {

    private RoleDtos() {
    }

    public record RoleDto(
            UUID roleId,
            String roleName,
            String description,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static RoleDto from(Role role) {
            return new RoleDto(
                    role.getRoleId(),
                    role.getRoleName(),
                    role.getDescription(),
                    role.getStatus() == null ? null : role.getStatus().name(),
                    role.getCreatedAt(),
                    role.getUpdatedAt()
            );
        }
    }

    @NoXssFields
    public record CreateRoleRequest(String roleName, String description) {
    }

    @NoXssFields
    public record UpdateRoleRequest(String roleName, String description) {
    }

}
