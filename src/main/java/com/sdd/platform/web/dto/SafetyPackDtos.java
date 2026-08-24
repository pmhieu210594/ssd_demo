package com.sdd.platform.web.dto;

import com.sdd.platform.domain.model.SafetyPackStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class SafetyPackDtos {

    private SafetyPackDtos() {}

    public record SafetyPackStatusDto(
            UUID safetyPackStatusId,
            UUID repositoryId,
            String repositoryNameMasked,
            boolean claudeMdExists,
            boolean settingsJsonExists,
            boolean rulesExists,
            int denyRuleCount,
            int askRuleCount,
            int allowRuleCount,
            boolean reviewedFlag,
            UUID reviewedByRoleId,
            OffsetDateTime lastUpdatedAt,
            OffsetDateTime collectedAt,
            String branchName,
            String commitSha,
            String scanStatus,
            String settingsParseStatus,
            String contentHash,
            String missingItemsSummary
    ) {
        public static SafetyPackStatusDto from(SafetyPackStatus status) {
            return new SafetyPackStatusDto(
                    status.getSafetyPackStatusId(),
                    status.getRepositoryId(),
                    status.getRepositoryNameMasked(),
                    Boolean.TRUE.equals(status.getClaudeMdExists()),
                    Boolean.TRUE.equals(status.getSettingsJsonExists()),
                    Boolean.TRUE.equals(status.getRulesExists()),
                    status.getDenyRuleCount() == null ? 0 : status.getDenyRuleCount(),
                    status.getAskRuleCount() == null ? 0 : status.getAskRuleCount(),
                    status.getAllowRuleCount() == null ? 0 : status.getAllowRuleCount(),
                    Boolean.TRUE.equals(status.getReviewedFlag()),
                    status.getReviewedByRoleId(),
                    status.getLastUpdatedAt(),
                    status.getCollectedAt(),
                    status.getBranchName(),
                    status.getCommitSha(),
                    status.getScanStatus(),
                    status.getSettingsParseStatus(),
                    status.getContentHash(),
                    status.getMissingItemsSummary()
            );
        }
    }
}
