package com.sdd.platform.application.usecase.qadashboard;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class QaDashboardModels {

    private QaDashboardModels() {
    }

    public record QaFilter(
            UUID projectId,
            UUID repositoryId,
            UUID ticketId,
            String search,
            int page,
            int size
    ) {
    }

    public record QaDashboardOption(
            String value,
            String label,
            String role
    ) {
    }

    public record QaDashboardOptions(
            java.util.List<QaDashboardOption> projects,
            java.util.List<QaDashboardOption> repositories,
            java.util.List<QaDashboardOption> tickets
    ) {
    }

    public record QaSummaryModel(
            double acTestCoveragePercent,
            int acNotTestedCount,
            double blackboxCoveragePercent,
            double testResultsPassPercent,
            int defectLeakageCount,
            int acceptanceReadyCount,
            OffsetDateTime updatedAt
    ) {
    }

    public record AcCoverageRow(
            String acId,
            String ticketKey,
            String status,
            String blackbox,
            String gap
    ) {
    }

    public record TrendPoint(
            String label,
            double coveragePercent
    ) {
    }

    public record AcCoverageCounts(
            int coveredCount,
            int notTestedCount,
            int totalCount
    ) {
    }

    public record TestRunCounts(
            int passedCount,
            int failedCount,
            int skippedCount,
            int totalCount
    ) {
    }

    public record AcCoveragePage(
            java.util.List<AcCoverageRow> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record QaTicketFilter(
            UUID projectId,
            UUID repositoryId,
            UUID ticketId,
            String search,
            String sortBy,
            String sortDir,
            int page,
            int size
    ) {
    }

    public record QaTicketRow(
            UUID ticketId,
            UUID projectId,
            String projectAlias,
            UUID repositoryId,
            String externalTicketKey,
            String title,
            String status,
            String priority,
            String ownerDisplay,
            double acCoveragePercent,
            double testResultPercent,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            Integer artifactVersion
    ) {
    }

    public record QaTicketPage(
            java.util.List<QaTicketRow> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record QaTicketDetail(
            QaTicketRow row,
            String description,
            String sprint,
            String latestCiRunUrl
    ) {
    }

    public record QaAcTicketRow(
            String acId,
            String acceptanceCriteria,
            String status,
            String linkedTestCase,
            String testResult,
            String owner
    ) {
    }

    public record QaAcTicketPage(
            java.util.List<QaAcTicketRow> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            int totalAcCount,
            int testedCount,
            int notTestedCount,
            double coveragePercent,
            double testResultPercent
    ) {
    }
}
