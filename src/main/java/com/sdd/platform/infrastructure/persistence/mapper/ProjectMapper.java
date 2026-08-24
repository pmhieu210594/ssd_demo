package com.sdd.platform.infrastructure.persistence.mapper;

import com.sdd.platform.domain.model.Project;
import com.sdd.platform.domain.model.ProjectTeamAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface ProjectMapper {

    Optional<Project> findById(@Param("projectId") UUID projectId);

    List<Project> findPage(
            @Param("keyword") String keyword,
            @Param("customerId") UUID customerId,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long count(
            @Param("keyword") String keyword,
            @Param("customerId") UUID customerId,
            @Param("status") String status
    );

    boolean existsActiveCustomer(@Param("customerId") UUID customerId);

    boolean existsActiveAlias(
            @Param("customerId") UUID customerId,
            @Param("projectAlias") String projectAlias,
            @Param("excludeProjectId") UUID excludeProjectId
    );

    List<UUID> findActiveTeamIdsByIds(@Param("teamIds") List<UUID> teamIds);

    void insert(Project project);

    int update(Project project);

    int softDelete(
            @Param("projectId") UUID projectId,
            @Param("deletedBy") String deletedBy,
            @Param("deletedAt") OffsetDateTime deletedAt,
            @Param("updatedBy") String updatedBy,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    List<ProjectTeamAssignment> findActiveTeamAssignments(@Param("projectId") UUID projectId);

    int deactivateTeamAssignment(
            @Param("projectId") UUID projectId,
            @Param("teamId") UUID teamId,
            @Param("deletedBy") String deletedBy,
            @Param("deletedAt") OffsetDateTime deletedAt,
            @Param("updatedBy") String updatedBy,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    int reactivateTeamAssignment(
            @Param("projectId") UUID projectId,
            @Param("teamId") UUID teamId,
            @Param("updatedBy") String updatedBy,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    void insertTeamAssignment(ProjectTeamAssignment assignment);
}
