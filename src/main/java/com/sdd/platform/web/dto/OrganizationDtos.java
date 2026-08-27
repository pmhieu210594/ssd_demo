package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.domain.model.Organization;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class OrganizationDtos {
    private OrganizationDtos() {}

    public record OrganizationDto(
            UUID organizationId,
            String organizationCode,
            String organizationName,
            String description,
            String status,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy,
            OffsetDateTime deletedAt,
            String deletedBy,
            long version
    ) {
        public static OrganizationDto from(Organization organization) {
            return new OrganizationDto(
                    organization.getOrganizationId(),
                    organization.getOrganizationCode(),
                    organization.getOrganizationName(),
                    organization.getDescription(),
                    organization.getStatus() == null ? null : organization.getStatus().name(),
                    organization.getCreatedAt(),
                    organization.getCreatedBy(),
                    organization.getUpdatedAt(),
                    organization.getUpdatedBy(),
                    organization.getDeletedAt(),
                    organization.getDeletedBy(),
                    organization.getVersion()
            );
        }
    }

    public record OrganizationPageDto(
            List<OrganizationDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static OrganizationPageDto from(PageResult<Organization> result) {
            return new OrganizationPageDto(
                    result.items().stream().map(OrganizationDto::from).toList(),
                    result.page(),
                    result.size(),
                    result.totalElements(),
                    result.totalPages()
            );
        }
    }

    public record CreateOrganizationRequest(
            String organizationCode,
            String organizationName,
            String description
    ) {
    }

    public record UpdateOrganizationRequest(
            String organizationCode,
            String organizationName,
            String description,
            String status,
            long version
    ) {
    }

    public record DeleteOrganizationRequest(long version) {
    }
}
