package com.sdd.platform.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    private Long id;
    private String projectKey;
    private UUID projectId;
    private UUID customerId;
    private String customerName;
    private String projectAlias;
    private String projectType;
    private RiskLevel riskLevel;
    private ProjectStatus status;
    private boolean deleteFlag;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private OffsetDateTime deletedAt;
    private String deletedBy;
    @Builder.Default
    private List<ProjectTeamAssignment> teamAssignments = List.of();

    public boolean isDeleted() {
        return deleteFlag || deletedAt != null || ProjectStatus.DELETED == status;
    }

    public enum ProjectStatus {
        ACTIVE,
        DELETED
    }

    public enum RiskLevel {
        INFO,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
