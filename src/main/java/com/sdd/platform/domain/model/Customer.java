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
public class Customer {

    private UUID customerId;
    private UUID organizationId;
    private String organizationName;
    private String customerCode;
    private String customerAlias;
    private CustomerClassification classification;
    private CustomerStatus status;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private OffsetDateTime deletedAt;
    private String deletedBy;
    private long version;

    public boolean isDeleted() {
        return CustomerStatus.DELETED == status || deletedAt != null;
    }

    public enum CustomerClassification {
        INTERNAL,
        EXTERNAL
    }

    public enum CustomerStatus {
        ACTIVE,
        DELETED
    }
}
