package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.domain.model.Project;
import com.sdd.platform.domain.model.ProjectTeamAssignment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepositoryPort {

    default Optional<Project> findById(Long projectId) {
        return Optional.empty();
    }

    Optional<Project> findById(UUID projectId);

    List<Project> findPage(
            String keyword,
            UUID customerId,
            String status,
            int offset,
            int limit
    );

    long count(String keyword, UUID customerId, String status);

    boolean existsActiveCustomer(UUID customerId);

    boolean existsActiveAlias(UUID customerId, String projectAlias, UUID excludeProjectId);

    List<UUID> findActiveTeamIdsByIds(List<UUID> teamIds);

    Project insert(Project project);

    int update(Project project);

    int softDelete(
            UUID projectId,
            String deletedBy,
            OffsetDateTime deletedAt,
            String updatedBy,
            OffsetDateTime updatedAt
    );

    List<ProjectTeamAssignment> findActiveTeamAssignments(UUID projectId);

    int deactivateTeamAssignment(
            UUID projectId,
            UUID teamId,
            String deletedBy,
            OffsetDateTime deletedAt,
            String updatedBy,
            OffsetDateTime updatedAt
    );

    int reactivateTeamAssignment(
            UUID projectId,
            UUID teamId,
            String updatedBy,
            OffsetDateTime updatedAt
    );

    void insertTeamAssignment(ProjectTeamAssignment assignment);
}
