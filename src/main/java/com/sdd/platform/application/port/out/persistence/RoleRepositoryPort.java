package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.domain.model.Role;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepositoryPort {

    List<Role> findActive(String keyword, String sort);

    Optional<Role> findById(UUID roleId);

    Optional<Role> findActiveById(UUID roleId);

    boolean existsActiveName(String roleName, UUID excludeRoleId);

    Role insert(Role role);

    int update(Role role);

    int logicalDelete(UUID roleId, String updatedBy, OffsetDateTime updatedAt);
}
