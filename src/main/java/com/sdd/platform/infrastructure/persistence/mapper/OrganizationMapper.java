package com.sdd.platform.infrastructure.persistence.mapper;

import com.sdd.platform.domain.model.Organization;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface OrganizationMapper {

    Optional<Organization> findById(@Param("organizationId") UUID organizationId);

    List<Organization> findPage(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long count(@Param("keyword") String keyword, @Param("status") String status);

    boolean existsActiveCode(
            @Param("organizationCode") String organizationCode,
            @Param("excludeOrganizationId") UUID excludeOrganizationId
    );

    boolean existsActiveName(
            @Param("organizationName") String organizationName,
            @Param("excludeOrganizationId") UUID excludeOrganizationId
    );

    void insert(Organization organization);

    int update(Organization organization);

    int softDelete(
            @Param("organizationId") UUID organizationId,
            @Param("version") long version,
            @Param("deletedBy") String deletedBy,
            @Param("deletedAt") OffsetDateTime deletedAt,
            @Param("updatedBy") String updatedBy,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    int deleteByCodePrefix(@Param("organizationCodePrefix") String organizationCodePrefix);
}
