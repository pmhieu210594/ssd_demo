package com.sdd.platform.domain.model;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CiRun {

    private UUID id;
    private UUID projectId;
    private UUID repositoryId;
    private UUID ticketId;
    private UUID pullRequestId;
    private UUID connectorRunId;
    private String ciProvider;
    private String externalRunId;
    private String workflowName;
    private String status;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private String ciUrl;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
