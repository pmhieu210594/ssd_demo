package com.sdd.platform.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityScan {

    private UUID securityScanId;
    private UUID repositoryId;
    private String repositoryNameMasked;
    private UUID ticketId;
    private UUID prId;
    private UUID ciRunId;
    private String branchName;
    private String commitSha;
    private Integer pullRequestNumber;
    private String workflowRunId;
    private String workflowJobName;
    private String scannerType;
    private String scannerName;
    private String scanTool;
    private String status;
    private String scanStatus;
    private String severity;
    private Integer findingCount;
    private Integer unresolvedCount;
    private Integer criticalCount;
    private Integer highCount;
    private Integer mediumCount;
    private Integer lowCount;
    private Integer infoCount;
    private String summary;
    private String scanCountsJson;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private OffsetDateTime collectedAt;
}
