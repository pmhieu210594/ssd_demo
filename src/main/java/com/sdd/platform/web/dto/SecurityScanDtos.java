package com.sdd.platform.web.dto;

import com.sdd.platform.domain.model.SecurityScan;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class SecurityScanDtos {

    private SecurityScanDtos() {}

    public record SecurityScanDto(
            UUID securityScanId,
            UUID repositoryId,
            String repositoryNameMasked,
            Integer pullRequestNumber,
            String branchName,
            String commitSha,
            String workflowRunId,
            String workflowJobName,
            String scannerType,
            String scannerName,
            String scanTool,
            String status,
            String scanStatus,
            String severity,
            int findingCount,
            int unresolvedCount,
            int criticalCount,
            int highCount,
            int mediumCount,
            int lowCount,
            int infoCount,
            String summary,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            OffsetDateTime collectedAt
    ) {
        public static SecurityScanDto from(SecurityScan scan) {
            return new SecurityScanDto(
                    scan.getSecurityScanId(),
                    scan.getRepositoryId(),
                    scan.getRepositoryNameMasked(),
                    scan.getPullRequestNumber(),
                    scan.getBranchName(),
                    scan.getCommitSha(),
                    scan.getWorkflowRunId(),
                    scan.getWorkflowJobName(),
                    scan.getScannerType(),
                    scan.getScannerName(),
                    scan.getScanTool(),
                    scan.getStatus(),
                    scan.getScanStatus(),
                    scan.getSeverity(),
                    scan.getFindingCount() == null ? 0 : scan.getFindingCount(),
                    scan.getUnresolvedCount() == null ? 0 : scan.getUnresolvedCount(),
                    scan.getCriticalCount() == null ? 0 : scan.getCriticalCount(),
                    scan.getHighCount() == null ? 0 : scan.getHighCount(),
                    scan.getMediumCount() == null ? 0 : scan.getMediumCount(),
                    scan.getLowCount() == null ? 0 : scan.getLowCount(),
                    scan.getInfoCount() == null ? 0 : scan.getInfoCount(),
                    scan.getSummary(),
                    scan.getStartedAt(),
                    scan.getFinishedAt(),
                    scan.getCollectedAt()
            );
        }
    }
}
