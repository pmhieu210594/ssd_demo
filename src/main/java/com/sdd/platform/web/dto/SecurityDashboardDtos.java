package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.ChecklistSectionResult;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.ExceptionCounts;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.ExceptionResult;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SafetyPackCounts;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SastScaCounts;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecretScanCounts;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityDashboardOption;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityDashboardOptions;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityScanResult;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecuritySummaryModel;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityTicketDetail;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityTicketPage;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityTicketRow;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class SecurityDashboardDtos {

    private SecurityDashboardDtos() {
    }

    public record SafetyPackCountsDto(int readyCount, int warningCount, int missingCount) {
        public static SafetyPackCountsDto from(SafetyPackCounts m) {
            return new SafetyPackCountsDto(m.readyCount(), m.warningCount(), m.missingCount());
        }
    }

    public record SecretScanCountsDto(int passCount, int failCount) {
        public static SecretScanCountsDto from(SecretScanCounts m) {
            return new SecretScanCountsDto(m.passCount(), m.failCount());
        }
    }

    public record SastScaCountsDto(int passCount, int warningCount, int failCount) {
        public static SastScaCountsDto from(SastScaCounts m) {
            return new SastScaCountsDto(m.passCount(), m.warningCount(), m.failCount());
        }
    }

    public record ExceptionCountsDto(int openCount, int totalCount) {
        public static ExceptionCountsDto from(ExceptionCounts m) {
            return new ExceptionCountsDto(m.openCount(), m.totalCount());
        }
    }

    public record SecurityDashboardOptionDto(
            String value,
            String label,
            String role
    ) {
        public static SecurityDashboardOptionDto from(SecurityDashboardOption option) {
            return new SecurityDashboardOptionDto(option.value(), option.label(), option.role());
        }
    }

    public record SecurityDashboardSummaryDto(
            SafetyPackCountsDto safetyPack,
            SecretScanCountsDto secretScan,
            SastScaCountsDto sastSca,
            ExceptionCountsDto exception,
            OffsetDateTime updatedAt
    ) {
        public static SecurityDashboardSummaryDto from(SecuritySummaryModel model) {
            return new SecurityDashboardSummaryDto(
                    SafetyPackCountsDto.from(model.safetyPack()),
                    SecretScanCountsDto.from(model.secretScan()),
                    SastScaCountsDto.from(model.sastSca()),
                    ExceptionCountsDto.from(model.exception()),
                    model.updatedAt()
            );
        }
    }

    public record SecurityDashboardOptionsDto(
            List<SecurityDashboardOptionDto> projects,
            List<SecurityDashboardOptionDto> repositories
    ) {
        public static SecurityDashboardOptionsDto from(SecurityDashboardOptions options) {
            return new SecurityDashboardOptionsDto(
                    options.projects().stream().map(SecurityDashboardOptionDto::from).toList(),
                    options.repositories().stream().map(SecurityDashboardOptionDto::from).toList()
            );
        }
    }

    public record SecurityTicketRowDto(
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
        public static SecurityTicketRowDto from(SecurityTicketRow row) {
            return new SecurityTicketRowDto(
                    row.ticketId(),
                    row.ticketKey(),
                    row.projectAlias(),
                    row.repositoryName(),
                    row.safetyStatus(),
                    row.secretScanStatus(),
                    row.sastStatus(),
                    row.scaStatus(),
                    row.exceptionStatus(),
                    row.finalVerdict(),
                    row.artifactVersion()
            );
        }
    }

    public record SecurityTicketPageDto(
            List<SecurityTicketRowDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
        public static SecurityTicketPageDto from(SecurityTicketPage page) {
            List<SecurityTicketRowDto> items = page.items().stream()
                    .map(SecurityTicketRowDto::from)
                    .toList();
            return new SecurityTicketPageDto(
                    items,
                    page.page(),
                    page.size(),
                    page.totalElements(),
                    page.totalPages(),
                    page.page() + 1 < page.totalPages()
            );
        }
    }

    public record SecurityScanResultDto(
            String scannerType,
            String scanStatus,
            String severity,
            int findingCount,
            int unresolvedCount
    ) {
        public static SecurityScanResultDto from(SecurityScanResult r) {
            return new SecurityScanResultDto(
                    r.scannerType(), r.scanStatus(), r.severity(), r.findingCount(), r.unresolvedCount());
        }
    }

    public record ChecklistSectionDto(String sectionType, boolean presentFlag, Boolean validFlag, String parseWarning) {
        public static ChecklistSectionDto from(ChecklistSectionResult r) {
            return new ChecklistSectionDto(r.sectionType(), r.presentFlag(), r.validFlag(), r.parseWarning());
        }
    }

    public record ExceptionResultDto(String exceptionType, boolean approved, String followUpStatus, LocalDate expiryDate) {
        public static ExceptionResultDto from(ExceptionResult r) {
            return new ExceptionResultDto(r.exceptionType(), r.approved(), r.followUpStatus(), r.expiryDate());
        }
    }

    public record SecurityTicketDetailDto(
            UUID ticketId,
            String ticketKey,
            String projectAlias,
            String repositoryName,
            String safetyStatus,
            List<SecurityScanResultDto> scans,
            List<ChecklistSectionDto> checklistSections,
            List<ExceptionResultDto> exceptions,
            String finalVerdict,
            Integer artifactVersion,
            String resolutionTime
    ) {
        public static SecurityTicketDetailDto from(SecurityTicketDetail d) {
            return new SecurityTicketDetailDto(
                    d.ticketId(),
                    d.ticketKey(),
                    d.projectAlias(),
                    d.repositoryName(),
                    d.safetyStatus(),
                    d.scans().stream().map(SecurityScanResultDto::from).toList(),
                    d.checklistSections().stream().map(ChecklistSectionDto::from).toList(),
                    d.exceptions().stream().map(ExceptionResultDto::from).toList(),
                    d.finalVerdict(),
                    d.artifactVersion(),
                    d.resolutionTime()
            );
        }
    }
}
