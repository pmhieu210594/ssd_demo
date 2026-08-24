package com.sdd.platform.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organization {

    private UUID organizationId;
    private String organizationCode;
    private String organizationName;
    private String description;
    private OrganizationStatus status;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private OffsetDateTime deletedAt;
    private String deletedBy;
    private long version;

    public boolean isDeleted() {
        return OrganizationStatus.DELETED == status || deletedAt != null;
    }

    public enum OrganizationStatus {
        ACTIVE,
        DELETED
    }
}
