package com.sdd.platform.application.usecase.securitydashboard;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SecurityDashboardModels {

    private SecurityDashboardModels() {
    }

    public record SecurityFilter(
            UUID projectId,
            UUID repositoryId,
            String search,
            String safetyStatus,
            String secretScanStatus,
            String sastStatus,
            String exceptionStatus,
            int page,
            int size
    ) {
    }

    public record SafetyPackCounts(
            int readyCount,
            int warningCount,
            int missingCount
    ) {
    }

    public record SecretScanCounts(
            int passCount,
            int failCount
    ) {
    }

    public record SastScaCounts(
            int passCount,
            int warningCount,
            int failCount
    ) {
    }

    public record ExceptionCounts(
            int openCount,
            int totalCount
    ) {
    }

    public record SecuritySummaryModel(
            SafetyPackCounts safetyPack,
            SecretScanCounts secretScan,
            SastScaCounts sastSca,
            ExceptionCounts exception,
            OffsetDateTime updatedAt
    ) {
    }

    public record SecurityDashboardOption(
            String value,
            String label,
            String role
    ) {
    }

    public record SecurityDashboardOptions(
            List<SecurityDashboardOption> projects,
            List<SecurityDashboardOption> repositories
    ) {
    }

    public record SecurityTicketRow(
            UUID ticketId,
            String ticketKey,
            String projectAlias,
            String repositoryName,
            String safetyStatus,
            String secretScanStatus,
            String sastStatus,
            String scaStatus,
            String exceptionStatus,
            String finalVerdict,
            Integer artifactVersion
    ) {
    }

    public record SecurityTicketPage(
            List<SecurityTicketRow> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record SecurityScanResult(
            String scannerType,
            String scanStatus,
            String severity,
            int findingCount,
            int unresolvedCount
    ) {
    }

    /**
     * Raw SAST scan snapshot used to derive Security Finding Resolution Time.
     * See SECURITY-FINDING-RESOLUTION-TIME spec-pack.md BR-1/BR-2.
     */
    public record SecurityScanSnapshot(
            int unresolvedCount,
            OffsetDateTime collectedAt
    ) {
    }

    public record ChecklistSectionResult(
            String sectionType,
            boolean presentFlag,
            Boolean validFlag,
            String parseWarning
    ) {
    }

    public record ExceptionResult(
            String exceptionType,
            boolean approved,
            String followUpStatus,
            LocalDate expiryDate
    ) {
    }

    public record SecurityTicketDetail(
            UUID ticketId,
            String ticketKey,
            String projectAlias,
            String repositoryName,
            String safetyStatus,
            List<SecurityScanResult> scans,
            List<ChecklistSectionResult> checklistSections,
            List<ExceptionResult> exceptions,
            String finalVerdict,
            Integer artifactVersion,
            String resolutionTime
    ) {
    }
}
