package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.RoleRepositoryPort;
import com.sdd.platform.domain.model.Role;
import com.sdd.platform.infrastructure.persistence.mapper.RoleMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RoleRepositoryAdapter implements RoleRepositoryPort {

    private final RoleMapper mapper;

    public RoleRepositoryAdapter(RoleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Role> findActive(String keyword, String sort) {
        return mapper.findActive(keyword, sort);
    }

    @Override
    public Optional<Role> findById(UUID roleId) {
        return mapper.findById(roleId);
    }

    @Override
    public Optional<Role> findActiveById(UUID roleId) {
        return mapper.findActiveById(roleId);
    }

    @Override
    public boolean existsActiveName(String roleName, UUID excludeRoleId) {
        return mapper.existsActiveName(roleName, excludeRoleId);
    }

    @Override
    public Role insert(Role role) {
        mapper.insert(role);
        return role;
    }

    @Override
    public int update(Role role) {
        return mapper.update(role);
    }

    @Override
    public int logicalDelete(UUID roleId, String updatedBy, OffsetDateTime updatedAt) {
        return mapper.logicalDelete(roleId, updatedBy, updatedAt);
    }
}
