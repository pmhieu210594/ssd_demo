package com.sdd.platform.domain.model;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RepositoryModel {

    private UUID repositoryId;
    private UUID projectId;
    private String projectAlias;
    private String repoNameMasked;
    private HostType hostType;
    private String defaultBranch;
    private String repoUrlHash;
    private RepositoryStatus status;
    private boolean deleteFlag;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private OffsetDateTime deletedAt;
    private String deletedBy;

    public boolean isDeleted() {
        return deleteFlag || deletedAt != null || status == RepositoryStatus.DELETED;
    }

    public enum HostType { GITHUB, LOCAL }
    public enum RepositoryStatus { ACTIVE, DELETED }
}
