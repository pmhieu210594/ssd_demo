package com.sdd.platform.application.usecase.dataopsdashboard;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DataOpsDashboardModels {

    private DataOpsDashboardModels() {
    }

    public record DataOpsDashboardFilter(
            UUID projectId,
            UUID repositoryId,
            String connectorName,
            String parserStatus,
            String search,
            int page,
            int size
    ) {
    }

    public record DataOpsDashboardOption(
            String value,
            String label,
            String role
    ) {
    }

    public record DataOpsDashboardOptions(
            List<DataOpsDashboardOption> projects,
            List<DataOpsDashboardOption> repositories,
            List<DataOpsDashboardOption> connectors
    ) {
    }

    /**
     * 5 confirmed KPIs only (spec-pack Output 6.3 / AC-DATAOPS-2..6). Security Alerts and Cost
     * Summary are out of scope pending H-DATAOPS-3/H-DATAOPS-4.
     */
    public record DataOpsDashboardSummary(
            long connectorFailureCount,
            long parseErrorCount,
            long missingEvidenceCount,
            long staleFreshnessCount,
            long brokenLinkCount,
            OffsetDateTime updatedAt
    ) {
    }

    public record DataOpsConnectorRow(
            UUID connectorId,
            UUID projectId,
            String projectAlias,
            UUID repositoryId,
            String repositoryName,
            String connectorName,
            String connectorType,
            String latestRunStatus,
            OffsetDateTime latestRunAt,
            long failedRunCount,
            long parseErrorCount,
            long missingEvidenceCount,
            Integer freshnessDelayMinutes
    ) {
    }

    public record DataOpsDashboardPage(
            List<DataOpsConnectorRow> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record DataOpsConnectorRunItem(
            UUID connectorRunId,
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            int recordsRead,
            int recordsWritten,
            String errorMessage
    ) {
    }

    public record DataOpsDataQualityItem(
            UUID dataQualityId,
            String sourceType,
            int missingCount,
            int parseErrorCount,
            int schemaViolationCount,
            String errorSummary,
            Integer freshnessDelayMinutes,
            OffsetDateTime checkedAt
    ) {
    }

    public record DataOpsMissingEvidenceItem(
            UUID artifactSnapshotId,
            UUID ticketId,
            String ticketExternalKey,
            String ticketTitle,
            String artifactTypeCode,
            String artifactName,
            String fileName,
            String sourcePath,
            boolean existsFlag,
            List<String> requiredFieldsMissing,
            List<String> missingSections,
            OffsetDateTime collectedAt
    ) {
    }

    public record DataOpsConnectorDetail(
            DataOpsConnectorRow row,
            List<DataOpsConnectorRunItem> recentRuns,
            List<DataOpsDataQualityItem> dataQualityChecks,
            List<DataOpsMissingEvidenceItem> missingEvidenceItems
    ) {
    }
}
