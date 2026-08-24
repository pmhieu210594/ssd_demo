package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.domain.model.Organization;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepositoryPort {

    Optional<Organization> findById(UUID organizationId);

    List<Organization> findPage(String keyword, String status, int offset, int limit);

    long count(String keyword, String status);

    boolean existsActiveCode(String organizationCode, UUID excludeOrganizationId);

    boolean existsActiveName(String organizationName, UUID excludeOrganizationId);

    Organization insert(Organization organization);

    int update(Organization organization);

    int softDelete(
            UUID organizationId,
            long version,
            String deletedBy,
            OffsetDateTime deletedAt,
            String updatedBy,
            OffsetDateTime updatedAt
    );

    int deleteByCodePrefix(String organizationCodePrefix);
}
