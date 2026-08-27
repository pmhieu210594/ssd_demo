package com.sdd.platform.infrastructure.persistence.mapper;

import com.sdd.platform.domain.model.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface RoleMapper {

    List<Role> findActive(@Param("keyword") String keyword, @Param("sort") String sort);

    Optional<Role> findById(@Param("roleId") UUID roleId);

    Optional<Role> findActiveById(@Param("roleId") UUID roleId);

    boolean existsActiveName(@Param("roleName") String roleName, @Param("excludeRoleId") UUID excludeRoleId);

    void insert(Role role);

    int update(Role role);

    int logicalDelete(
            @Param("roleId") UUID roleId,
            @Param("updatedBy") String updatedBy,
            @Param("updatedAt") OffsetDateTime updatedAt
    );
}
