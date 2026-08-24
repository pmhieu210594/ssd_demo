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
public class ProjectTeamAssignment {

    private UUID projectTeamId;
    private UUID projectId;
    private UUID teamId;
    private String teamCode;
    private String teamName;
    private Project.ProjectStatus status;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private OffsetDateTime deletedAt;
    private String deletedBy;

    public boolean isDeleted() {
        return deletedAt != null || Project.ProjectStatus.DELETED == status;
    }
}
