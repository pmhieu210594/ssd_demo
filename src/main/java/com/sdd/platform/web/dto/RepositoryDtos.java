package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.domain.model.RepositoryModel;
import com.sdd.platform.web.validation.NoXssFields;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class RepositoryDtos {
    private RepositoryDtos() {}

    public record RepositoryDto(
            UUID repositoryId,
            UUID projectId,
            String projectAlias,
            String repoNameMasked,
            String hostType,
            String defaultBranch,
            String repoUrlHash,
            String status,
            boolean deleteFlag,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy,
            OffsetDateTime deletedAt,
            String deletedBy
    ) {
        public static RepositoryDto from(RepositoryModel repository) {
            return new RepositoryDto(
                    repository.getRepositoryId(),
                    repository.getProjectId(),
                    repository.getProjectAlias(),
                    repository.getRepoNameMasked(),
                    repository.getHostType() == null ? null : repository.getHostType().name(),
                    repository.getDefaultBranch(),
                    repository.getRepoUrlHash(),
                    repository.getStatus() == null ? null : repository.getStatus().name(),
                    repository.isDeleteFlag(),
                    repository.getCreatedAt(),
                    repository.getCreatedBy(),
                    repository.getUpdatedAt(),
                    repository.getUpdatedBy(),
                    repository.getDeletedAt(),
                    repository.getDeletedBy()
            );
        }
    }

    public record RepositoryPageDto(
            List<RepositoryDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static RepositoryPageDto from(PageResult<RepositoryModel> result) {
            return new RepositoryPageDto(
                    result.items().stream().map(RepositoryDto::from).toList(),
                    result.page(),
                    result.size(),
                    result.totalElements(),
                    result.totalPages()
            );
        }
    }

    @NoXssFields
    public record CreateRepositoryRequest(
            UUID projectId,
            String repo_name_masked,
            String host_type,
            String default_branch,
            String repo_url_hash
    ) {}

    @NoXssFields
    public record UpdateRepositoryRequest(
            UUID projectId,
            String repo_name_masked,
            String host_type,
            String default_branch,
            String repo_url_hash
    ) {}
}
