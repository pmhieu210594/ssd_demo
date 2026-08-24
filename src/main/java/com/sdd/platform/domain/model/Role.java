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
public class Role {

    private UUID roleId;
    private String roleName;
    private String description;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private int deleteFlag;
    private RoleStatus status;

    public boolean isDeleted() {
        return deleteFlag == 1 || status == RoleStatus.DELETED;
    }

    public enum RoleStatus {
        ACTIVE,
        DELETED
    }
}
