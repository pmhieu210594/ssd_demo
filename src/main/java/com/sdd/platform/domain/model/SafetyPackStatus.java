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
public class SafetyPackStatus {

    private UUID safetyPackStatusId;
    private UUID repositoryId;
    private String repositoryNameMasked;
    private Boolean claudeMdExists;
    private Boolean settingsJsonExists;
    private Boolean rulesExists;
    private Integer denyRuleCount;
    private Integer askRuleCount;
    private Integer allowRuleCount;
    private Boolean reviewedFlag;
    private UUID reviewedByRoleId;
    private OffsetDateTime lastUpdatedAt;
    private OffsetDateTime collectedAt;
    private String branchName;
    private String commitSha;
    private String scanStatus;
    private String settingsParseStatus;
    private String contentHash;
    private String missingItemsSummary;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;
}
