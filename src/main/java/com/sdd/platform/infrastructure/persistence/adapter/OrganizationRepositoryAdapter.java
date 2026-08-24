package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.OrganizationRepositoryPort;
import com.sdd.platform.domain.model.Organization;
import com.sdd.platform.infrastructure.persistence.mapper.OrganizationMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class OrganizationRepositoryAdapter implements OrganizationRepositoryPort {

    private final OrganizationMapper mapper;

    public OrganizationRepositoryAdapter(OrganizationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Organization> findById(UUID organizationId) {
        return mapper.findById(organizationId);
    }

    @Override
    public List<Organization> findPage(String keyword, String status, int offset, int limit) {
        return mapper.findPage(keyword, status, offset, limit);
    }

    @Override
    public long count(String keyword, String status) {
        return mapper.count(keyword, status);
    }

    @Override
    public boolean existsActiveCode(String organizationCode, UUID excludeOrganizationId) {
        return mapper.existsActiveCode(organizationCode, excludeOrganizationId);
    }

    @Override
    public boolean existsActiveName(String organizationName, UUID excludeOrganizationId) {
        return mapper.existsActiveName(organizationName, excludeOrganizationId);
    }

    @Override
    public Organization insert(Organization organization) {
        mapper.insert(organization);
        return organization;
    }

    @Override
    public int update(Organization organization) {
        return mapper.update(organization);
    }

    @Override
    public int softDelete(
            UUID organizationId,
            long version,
            String deletedBy,
            OffsetDateTime deletedAt,
            String updatedBy,
            OffsetDateTime updatedAt
    ) {
        return mapper.softDelete(organizationId, version, deletedBy, deletedAt, updatedBy, updatedAt);
    }

    @Override
    public int deleteByCodePrefix(String organizationCodePrefix) {
        return mapper.deleteByCodePrefix(organizationCodePrefix);
    }
}
