package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.domain.model.RepositoryModel;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositoryRepositoryPort {

    Optional<RepositoryModel> findById(UUID repositoryId);

    List<RepositoryModel> findPage(UUID projectId, String status, String keyword, int offset, int limit);

    long count(UUID projectId, String status, String keyword);

    boolean existsActiveName(UUID projectId, String repoNameMasked, UUID excludeRepositoryId);

    boolean existsActiveProject(UUID projectId);

    RepositoryModel insert(RepositoryModel repository);

    int update(RepositoryModel repository);

    int softDelete(
            UUID repositoryId,
            String deletedBy,
            OffsetDateTime deletedAt,
            String updatedBy,
            OffsetDateTime updatedAt
    );
}
