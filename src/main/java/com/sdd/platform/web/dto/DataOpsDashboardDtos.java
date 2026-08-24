package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.dataopsdashboard.DataOpsDashboardModels;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DataOpsDashboardDtos {

    private DataOpsDashboardDtos() {
    }

    public record DataOpsDashboardOptionDto(
            String value,
            String label,
            String role
    ) {
        public static DataOpsDashboardOptionDto from(DataOpsDashboardModels.DataOpsDashboardOption option) {
            return new DataOpsDashboardOptionDto(option.value(), option.label(), option.role());
        }
    }

    public record DataOpsDashboardOptionsDto(
            List<DataOpsDashboardOptionDto> projects,
            List<DataOpsDashboardOptionDto> repositories,
            List<DataOpsDashboardOptionDto> connectors
    ) {
        public static DataOpsDashboardOptionsDto from(DataOpsDashboardModels.DataOpsDashboardOptions options) {
            return new DataOpsDashboardOptionsDto(
                    options.projects().stream().map(DataOpsDashboardOptionDto::from).toList(),
                    options.repositories().stream().map(DataOpsDashboardOptionDto::from).toList(),
                    options.connectors().stream().map(DataOpsDashboardOptionDto::from).toList()
            );
        }
    }

    public record DataOpsDashboardSummaryDto(
            long connectorFailureCount,
            long parseErrorCount,
            long missingEvidenceCount,
            long staleFreshnessCount,
            long brokenLinkCount,
            OffsetDateTime updatedAt
    ) {
        public static DataOpsDashboardSummaryDto from(DataOpsDashboardModels.DataOpsDashboardSummary summary) {
            return new DataOpsDashboardSummaryDto(
                    summary.connectorFailureCount(),
                    summary.parseErrorCount(),
                    summary.missingEvidenceCount(),
                    summary.staleFreshnessCount(),
                    summary.brokenLinkCount(),
                    summary.updatedAt()
            );
        }
    }

    public record DataOpsConnectorRowDto(
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
        public static DataOpsConnectorRowDto from(DataOpsDashboardModels.DataOpsConnectorRow row) {
            return new DataOpsConnectorRowDto(
                    row.connectorId(),
                    row.projectId(),
                    row.projectAlias(),
                    row.repositoryId(),
                    row.repositoryName(),
                    row.connectorName(),
                    row.connectorType(),
                    row.latestRunStatus(),
                    row.latestRunAt(),
                    row.failedRunCount(),
                    row.parseErrorCount(),
                    row.missingEvidenceCount(),
                    row.freshnessDelayMinutes()
            );
        }
    }

    public record DataOpsDashboardPageDto(
            List<DataOpsConnectorRowDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
        public static DataOpsDashboardPageDto from(DataOpsDashboardModels.DataOpsDashboardPage page) {
            return new DataOpsDashboardPageDto(
                    page.items().stream().map(DataOpsConnectorRowDto::from).toList(),
                    page.page(),
                    page.size(),
                    page.totalElements(),
                    page.totalPages(),
                    page.page() < page.totalPages()
            );
        }
    }

    public record DataOpsConnectorRunItemDto(
            UUID connectorRunId,
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            int recordsRead,
            int recordsWritten,
            String errorMessage
    ) {
        public static DataOpsConnectorRunItemDto from(DataOpsDashboardModels.DataOpsConnectorRunItem item) {
            return new DataOpsConnectorRunItemDto(
                    item.connectorRunId(),
                    item.status(),
                    item.startedAt(),
                    item.finishedAt(),
                    item.recordsRead(),
                    item.recordsWritten(),
                    item.errorMessage()
            );
        }
    }

    public record DataOpsDataQualityItemDto(
            UUID dataQualityId,
            String sourceType,
            int missingCount,
            int parseErrorCount,
            int schemaViolationCount,
            String errorSummary,
            Integer freshnessDelayMinutes,
            OffsetDateTime checkedAt
    ) {
        public static DataOpsDataQualityItemDto from(DataOpsDashboardModels.DataOpsDataQualityItem item) {
            return new DataOpsDataQualityItemDto(
                    item.dataQualityId(),
                    item.sourceType(),
                    item.missingCount(),
                    item.parseErrorCount(),
                    item.schemaViolationCount(),
                    item.errorSummary(),
                    item.freshnessDelayMinutes(),
                    item.checkedAt()
            );
        }
    }

    public record DataOpsMissingEvidenceItemDto(
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
        public static DataOpsMissingEvidenceItemDto from(DataOpsDashboardModels.DataOpsMissingEvidenceItem item) {
            return new DataOpsMissingEvidenceItemDto(
                    item.artifactSnapshotId(),
                    item.ticketId(),
                    item.ticketExternalKey(),
                    item.ticketTitle(),
                    item.artifactTypeCode(),
                    item.artifactName(),
                    item.fileName(),
                    item.sourcePath(),
                    item.existsFlag(),
                    item.requiredFieldsMissing(),
                    item.missingSections(),
                    item.collectedAt()
            );
        }
    }

    public record DataOpsConnectorDetailDto(
            DataOpsConnectorRowDto row,
            List<DataOpsConnectorRunItemDto> recentRuns,
            List<DataOpsDataQualityItemDto> dataQualityChecks,
            List<DataOpsMissingEvidenceItemDto> missingEvidenceItems
    ) {
        public static DataOpsConnectorDetailDto from(DataOpsDashboardModels.DataOpsConnectorDetail detail) {
            return new DataOpsConnectorDetailDto(
                    DataOpsConnectorRowDto.from(detail.row()),
                    detail.recentRuns().stream().map(DataOpsConnectorRunItemDto::from).toList(),
                    detail.dataQualityChecks().stream().map(DataOpsDataQualityItemDto::from).toList(),
                    detail.missingEvidenceItems().stream().map(DataOpsMissingEvidenceItemDto::from).toList()
            );
        }
    }

    public record DataOpsRepositoryMissingEvidenceDto(
            List<DataOpsMissingEvidenceItemDto> missingEvidenceItems
    ) {
        public static DataOpsRepositoryMissingEvidenceDto from(List<DataOpsDashboardModels.DataOpsMissingEvidenceItem> items) {
            return new DataOpsRepositoryMissingEvidenceDto(
                    items.stream().map(DataOpsMissingEvidenceItemDto::from).toList()
            );
        }
    }
}
