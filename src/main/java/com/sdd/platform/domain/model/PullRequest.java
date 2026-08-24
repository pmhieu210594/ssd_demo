package com.sdd.platform.domain.model;

import lombok.*;

import java.time.OffsetDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PullRequest {

    private Long id;
    private Long repositoryId;
    private Long ticketId;
    private Integer externalPrNumber;
    private String title;
    private State state;
    private String authorLogin;
    private String baseBranch;
    private String headBranch;
    private OffsetDateTime openedAt;
    private OffsetDateTime mergedAt;
    private OffsetDateTime closedAt;
    private Integer reviewCount;
    private Integer commentsCount;
    private Integer additions;
    private Integer deletions;
    private Integer changedFiles;
    private String linkUrl;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public enum State { OPEN, MERGED, CLOSED }
}
