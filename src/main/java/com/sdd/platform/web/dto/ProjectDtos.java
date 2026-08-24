package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.domain.model.Project;
import com.sdd.platform.domain.model.ProjectTeamAssignment;
import com.sdd.platform.web.validation.NoXssFields;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ProjectDtos {
    private ProjectDtos() {}

    public record ProjectTeamAssignmentDto(
            UUID projectTeamId,
            UUID teamId,
            String teamCode,
            String teamName,
            String status,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy,
            OffsetDateTime deletedAt,
            String deletedBy
    ) {
        public static ProjectTeamAssignmentDto from(ProjectTeamAssignment assignment) {
            return new ProjectTeamAssignmentDto(
                    assignment.getProjectTeamId(),
                    assignment.getTeamId(),
                    assignment.getTeamCode(),
                    assignment.getTeamName(),
                    assignment.getStatus() == null ? null : assignment.getStatus().name(),
                    assignment.getCreatedAt(),
                    assignment.getCreatedBy(),
                    assignment.getUpdatedAt(),
                    assignment.getUpdatedBy(),
                    assignment.getDeletedAt(),
                    assignment.getDeletedBy()
            );
        }
    }

    public record ProjectDto(
            UUID projectId,
            UUID customerId,
            String customerName,
            String projectAlias,
            String projectType,
            String riskLevel,
            String status,
            boolean deleteFlag,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy,
            OffsetDateTime deletedAt,
            String deletedBy,
            List<ProjectTeamAssignmentDto> teamAssignments
    ) {
        public static ProjectDto from(Project project) {
            return new ProjectDto(
                    project.getProjectId(),
                    project.getCustomerId(),
                    project.getCustomerName(),
                    project.getProjectAlias(),
                    project.getProjectType(),
                    project.getRiskLevel() == null ? null : project.getRiskLevel().name(),
                    project.getStatus() == null ? null : project.getStatus().name(),
                    project.isDeleteFlag(),
                    project.getCreatedAt(),
                    project.getCreatedBy(),
                    project.getUpdatedAt(),
                    project.getUpdatedBy(),
                    project.getDeletedAt(),
                    project.getDeletedBy(),
                    project.getTeamAssignments() == null
                            ? List.of()
                            : project.getTeamAssignments().stream().map(ProjectTeamAssignmentDto::from).toList()
            );
        }
    }

    public record ProjectPageDto(
            List<ProjectDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static ProjectPageDto from(PageResult<Project> result) {
            return new ProjectPageDto(
                    result.items().stream().map(ProjectDto::from).toList(),
                    result.page(),
                    result.size(),
                    result.totalElements(),
                    result.totalPages()
            );
        }
    }

    @NoXssFields
    public record CreateProjectRequest(
            UUID customerId,
            String projectAlias,
            String projectType,
            String riskLevel,
            List<UUID> teamIds
    ) {
    }

    @NoXssFields
    public record UpdateProjectRequest(
            UUID customerId,
            String projectAlias,
            String projectType,
            String riskLevel,
            List<UUID> teamIds
    ) {
    }
}
