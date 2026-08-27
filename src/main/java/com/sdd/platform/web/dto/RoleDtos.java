package com.sdd.platform.web.dto;

import com.sdd.platform.domain.model.Role;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class RoleDtos {

    private RoleDtos() {
    }

    public record RoleDto(
            UUID roleId,
            String roleName,
            String description,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static RoleDto from(Role role) {
            return new RoleDto(
                    role.getRoleId(),
                    role.getRoleName(),
                    role.getDescription(),
                    role.getCreatedAt(),
                    role.getUpdatedAt()
            );
        }
    }

    public record CreateRoleRequest(String roleName, String description) {
    }

    public record UpdateRoleRequest(String roleName, String description) {
    }

}
