package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.ProjectRepositoryPort;
import com.sdd.platform.domain.model.Project;
import com.sdd.platform.domain.model.ProjectTeamAssignment;
import com.sdd.platform.infrastructure.persistence.mapper.ProjectMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProjectRepositoryAdapter implements ProjectRepositoryPort {

    private final ProjectMapper mapper;

    public ProjectRepositoryAdapter(ProjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Project> findById(UUID projectId) {
        return mapper.findById(projectId);
    }

    @Override
    public List<Project> findPage(String keyword, UUID customerId, String status, int offset, int limit) {
        return mapper.findPage(keyword, customerId, status, offset, limit);
    }

    @Override
    public long count(String keyword, UUID customerId, String status) {
        return mapper.count(keyword, customerId, status);
    }

    @Override
    public boolean existsActiveCustomer(UUID customerId) {
        return mapper.existsActiveCustomer(customerId);
    }

    @Override
    public boolean existsActiveAlias(UUID customerId, String projectAlias, UUID excludeProjectId) {
        return mapper.existsActiveAlias(customerId, projectAlias, excludeProjectId);
    }

    @Override
    public List<UUID> findActiveTeamIdsByIds(List<UUID> teamIds) {
        return mapper.findActiveTeamIdsByIds(teamIds);
    }

    @Override
    public Project insert(Project project) {
        mapper.insert(project);
        return project;
    }

    @Override
    public int update(Project project) {
        return mapper.update(project);
    }

    @Override
    public int softDelete(UUID projectId, String deletedBy, OffsetDateTime deletedAt, String updatedBy, OffsetDateTime updatedAt) {
        return mapper.softDelete(projectId, deletedBy, deletedAt, updatedBy, updatedAt);
    }

    @Override
    public List<ProjectTeamAssignment> findActiveTeamAssignments(UUID projectId) {
        return mapper.findActiveTeamAssignments(projectId);
    }

    @Override
    public int deactivateTeamAssignment(UUID projectId, UUID teamId, String deletedBy, OffsetDateTime deletedAt, String updatedBy, OffsetDateTime updatedAt) {
        return mapper.deactivateTeamAssignment(projectId, teamId, deletedBy, deletedAt, updatedBy, updatedAt);
    }

    @Override
    public int reactivateTeamAssignment(UUID projectId, UUID teamId, String updatedBy, OffsetDateTime updatedAt) {
        return mapper.reactivateTeamAssignment(projectId, teamId, updatedBy, updatedAt);
    }

    @Override
    public void insertTeamAssignment(ProjectTeamAssignment assignment) {
        mapper.insertTeamAssignment(assignment);
    }
}
