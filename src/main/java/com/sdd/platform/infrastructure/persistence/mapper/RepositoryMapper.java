package com.sdd.platform.infrastructure.persistence.mapper;

import com.sdd.platform.domain.model.RepositoryModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface RepositoryMapper {

    Optional<RepositoryModel> findById(@Param("repositoryId") UUID repositoryId);

    List<RepositoryModel> findPage(@Param("projectId") UUID projectId, 
        @Param("status") String status, @Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);

    long count(@Param("projectId") UUID projectId, @Param("status") String status, @Param("keyword") String keyword);

    boolean existsActiveName(
            @Param("projectId") UUID projectId,
            @Param("repoNameMasked") String repoNameMasked,
            @Param("excludeRepositoryId") UUID excludeRepositoryId
    );

    boolean existsActiveProject(@Param("projectId") UUID projectId);

    void insert(RepositoryModel repository);

    int update(RepositoryModel repository);

    int softDelete(
            @Param("repositoryId") UUID repositoryId,
            @Param("deletedBy") String deletedBy,
            @Param("deletedAt") OffsetDateTime deletedAt,
            @Param("updatedBy") String updatedBy,
            @Param("updatedAt") OffsetDateTime updatedAt
    );
}
