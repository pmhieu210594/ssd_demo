package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.AcCoveragePage;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.AcCoverageRow;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaAcTicketPage;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaAcTicketRow;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaDashboardOption;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaDashboardOptions;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaSummaryModel;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaTicketDetail;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaTicketPage;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaTicketRow;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.TrendPoint;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class QaDashboardDtos {

    private QaDashboardDtos() {
    }

    public record QaDashboardSummaryDto(
            double acTestCoveragePercent,
            int acNotTestedCount,
            double blackboxCoveragePercent,
            double testResultsPassPercent,
            int defectLeakageCount,
            int acceptanceReadyCount,
            OffsetDateTime updatedAt
    ) {
        public static QaDashboardSummaryDto from(QaSummaryModel model) {
            return new QaDashboardSummaryDto(
                    model.acTestCoveragePercent(),
                    model.acNotTestedCount(),
                    model.blackboxCoveragePercent(),
                    model.testResultsPassPercent(),
                    model.defectLeakageCount(),
                    model.acceptanceReadyCount(),
                    model.updatedAt()
            );
        }
    }

    public record AcceptanceCriteriaRowDto(
            String acId,
            String ticketKey,
            String status,
            String blackbox,
            String gap
    ) {
        public static AcceptanceCriteriaRowDto from(AcCoverageRow row) {
            return new AcceptanceCriteriaRowDto(
                    row.acId(),
                    row.ticketKey(),
                    row.status(),
                    row.blackbox(),
                    row.gap()
            );
        }
    }

    public record AcceptanceCriteriaPageDto(
            List<AcceptanceCriteriaRowDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
        public static AcceptanceCriteriaPageDto from(AcCoveragePage page) {
            List<AcceptanceCriteriaRowDto> items = page.items().stream()
                    .map(AcceptanceCriteriaRowDto::from)
                    .toList();
            return new AcceptanceCriteriaPageDto(
                    items,
                    page.page(),
                    page.size(),
                    page.totalElements(),
                    page.totalPages(),
                    page.page() + 1 < page.totalPages()
            );
        }
    }

    public record AcCoverageTrendPointDto(
            String label,
            double coveragePercent
    ) {
        public static AcCoverageTrendPointDto from(TrendPoint point) {
            return new AcCoverageTrendPointDto(point.label(), point.coveragePercent());
        }
    }

    public record QaDashboardOptionDto(
            String value,
            String label,
            String role
    ) {
        public static QaDashboardOptionDto from(QaDashboardOption option) {
            return new QaDashboardOptionDto(option.value(), option.label(), option.role());
        }
    }

    public record QaDashboardOptionsDto(
            List<QaDashboardOptionDto> projects,
            List<QaDashboardOptionDto> repositories,
            List<QaDashboardOptionDto> tickets
    ) {
        public static QaDashboardOptionsDto from(QaDashboardOptions options) {
            return new QaDashboardOptionsDto(
                    options.projects().stream().map(QaDashboardOptionDto::from).toList(),
                    options.repositories().stream().map(QaDashboardOptionDto::from).toList(),
                    options.tickets().stream().map(QaDashboardOptionDto::from).toList()
            );
        }
    }

    public record QaTicketRowDto(
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
        public static QaTicketRowDto from(QaTicketRow row) {
            return new QaTicketRowDto(
                    row.ticketId(),
                    row.projectId(),
                    row.projectAlias(),
                    row.repositoryId(),
                    row.externalTicketKey(),
                    row.title(),
                    row.status(),
                    row.priority(),
                    row.ownerDisplay(),
                    row.acCoveragePercent(),
                    row.testResultPercent(),
                    row.createdAt(),
                    row.updatedAt(),
                    row.artifactVersion()
            );
        }
    }

    public record QaTicketPageDto(
            List<QaTicketRowDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
        public static QaTicketPageDto from(QaTicketPage page) {
            return new QaTicketPageDto(
                    page.items().stream().map(QaTicketRowDto::from).toList(),
                    page.page(),
                    page.size(),
                    page.totalElements(),
                    page.totalPages(),
                    page.page() + 1 < page.totalPages()
            );
        }
    }

    public record QaTicketDetailDto(
            QaTicketRowDto row,
            String description,
            String sprint,
            String latestCiRunUrl
    ) {
        public static QaTicketDetailDto from(QaTicketDetail detail) {
            return new QaTicketDetailDto(
                    QaTicketRowDto.from(detail.row()),
                    detail.description(),
                    detail.sprint(),
                    detail.latestCiRunUrl()
            );
        }
    }

    public record QaAcTicketRowDto(
            String acId,
            String acceptanceCriteria,
            String status,
            String linkedTestCase,
            String testResult,
            String owner
    ) {
        public static QaAcTicketRowDto from(QaAcTicketRow row) {
            return new QaAcTicketRowDto(
                    row.acId(),
                    row.acceptanceCriteria(),
                    row.status(),
                    row.linkedTestCase(),
                    row.testResult(),
                    row.owner()
            );
        }
    }

    public record QaAcTicketPageDto(
            List<QaAcTicketRowDto> items,
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
        public static QaAcTicketPageDto from(QaAcTicketPage page) {
            return new QaAcTicketPageDto(
                    page.items().stream().map(QaAcTicketRowDto::from).toList(),
                    page.page(),
                    page.size(),
                    page.totalElements(),
                    page.totalPages(),
                    page.totalAcCount(),
                    page.testedCount(),
                    page.notTestedCount(),
                    page.coveragePercent(),
                    page.testResultPercent()
            );
        }
    }
}
