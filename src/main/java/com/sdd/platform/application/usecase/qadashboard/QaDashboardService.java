package com.sdd.platform.application.usecase.qadashboard;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.QaDashboardRepositoryPort;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.AcCoverageCounts;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.AcCoveragePage;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaAcTicketPage;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaDashboardOptions;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaFilter;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaSummaryModel;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaTicketDetail;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaTicketFilter;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaTicketPage;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.TestRunCounts;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.TrendPoint;
import com.sdd.platform.domain.model.AuthUserContext;
import com.sdd.platform.domain.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class QaDashboardService {

    // Blackbox parser not yet implemented — placeholder until parser writes to
    // tbl_fact_artifact_parsed_section
    private static final double BLACKBOX_COVERAGE_PLACEHOLDER = 0.0;

    // Acceptance readiness formula deferred (H-QA-DASHBOARD-4)
    private static final int ACCEPTANCE_READY_PLACEHOLDER = 0;

    private static final int MAX_SEARCH_LENGTH = 200;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final QaDashboardRepositoryPort repository;

    public QaDashboardService(QaDashboardRepositoryPort repository) {
        this.repository = repository;
    }

    /** Route-entry gate: does the caller hold the QA role on *any* project (or ADMIN)? */
    public void requireAnyAccess(AuthUserContext caller) {
        if (caller == null || !repository.hasDashboardAccess(caller)) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    /**
     * Gates access to a specific project's QA data. Only ADMIN bypasses this
     * gate; otherwise the caller must hold the QA project role for the given
     * projectId — holding QA on a different project is not sufficient.
     */
    public void requireQaAccess(AuthUserContext caller, UUID projectId) {
        if (caller == null || caller.getRole() == null) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
        String role = caller.getRole().trim().toUpperCase(Locale.ROOT);
        if ("ADMIN".equals(role)) {
            return;
        }
        if (projectId == null) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
        String projectRole = repository.findProjectRole(caller, projectId);
        if (!"QA".equalsIgnoreCase(projectRole)) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    @Transactional(readOnly = true)
    public QaSummaryModel getSummary(UUID projectId, UUID repositoryId,
            UUID ticketId, String search) {
        QaFilter filter = normalize(projectId, repositoryId, ticketId, search, 0, DEFAULT_PAGE_SIZE);

        AcCoverageCounts acCounts = repository.findAcCoverageCounts(filter);
        int defectCount = repository.findDefectLeakageCount(filter);
        TestRunCounts testCounts = repository.findTestRunCounts(filter);

        int testedCount = acCounts.totalCount() - acCounts.notTestedCount();
        double acCoveragePercent = acCounts.totalCount() == 0 ? 0.0
                : round1((double) testedCount / acCounts.totalCount() * 100);

        double testPassPercent = testCounts.totalCount() == 0 ? 0.0
                : round1((double) testCounts.passedCount() / testCounts.totalCount() * 100);

        return new QaSummaryModel(
                acCoveragePercent,
                acCounts.notTestedCount(),
                BLACKBOX_COVERAGE_PLACEHOLDER,
                testPassPercent,
                defectCount,
                ACCEPTANCE_READY_PLACEHOLDER,
                OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public AcCoveragePage getAcceptanceCriteria(UUID projectId, UUID repositoryId,
            UUID ticketId, String search,
            int page, int size) {
        QaFilter filter = normalize(projectId, repositoryId, ticketId, search, page, size);
        return repository.findAcCoverageRows(filter);
    }

    @Transactional(readOnly = true)
    public List<TrendPoint> getCoverageTrend(UUID projectId, UUID repositoryId,
            UUID ticketId, String search) {
        QaFilter filter = normalize(projectId, repositoryId, ticketId, search, 0, DEFAULT_PAGE_SIZE);
        return repository.findCoverageTrendPoints(filter);
    }

    @Transactional(readOnly = true)
    public QaDashboardOptions getOptions(UUID projectId, UUID repositoryId, AuthUserContext caller) {
        return repository.findOptions(projectId, repositoryId, caller);
    }

    @Transactional(readOnly = true)
    public QaTicketPage getTickets(UUID projectId, UUID repositoryId, UUID ticketId, String search,
            String sortBy, String sortDir, int page, int size) {
        String cleanSearch = cleanSearch(search);
        int clampedSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int clampedPage = Math.max(page, 0);
        QaTicketFilter filter = new QaTicketFilter(
                projectId, repositoryId, ticketId, cleanSearch, sortBy, sortDir, clampedPage, clampedSize);
        return repository.findTickets(filter);
    }

    @Transactional(readOnly = true)
    public QaTicketDetail getTicketDetail(UUID ticketId, AuthUserContext caller) {
        QaTicketDetail detail = repository.findTicketDetail(ticketId)
                .map(row -> new QaTicketDetail(row, null, null, repository.findLatestCiRunUrl(ticketId).orElse(null)))
                .orElseThrow(() -> new NotFoundException("Pages.QaDashboard.NotFound"));
        requireQaAccess(caller, detail.row().projectId());
        return detail;
    }

    @Transactional(readOnly = true)
    public QaAcTicketPage getTicketAcceptanceCriteria(UUID ticketId, String search, int page, int size,
            AuthUserContext caller) {
        requireQaAccess(caller, findTicketProjectId(ticketId));
        String cleanSearch = cleanSearch(search);
        int clampedSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int clampedPage = Math.max(page, 0);
        return repository.findTicketAcceptanceCriteria(ticketId, cleanSearch, clampedPage, clampedSize);
    }

    @Transactional(readOnly = true)
    public List<TrendPoint> getTicketCoverageTrend(UUID ticketId, AuthUserContext caller) {
        requireQaAccess(caller, findTicketProjectId(ticketId));
        return repository.findTicketCoverageTrend(ticketId);
    }

    private UUID findTicketProjectId(UUID ticketId) {
        return repository.findTicketDetail(ticketId)
                .map(QaDashboardModels.QaTicketRow::projectId)
                .orElseThrow(() -> new NotFoundException("Pages.QaDashboard.NotFound"));
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(UUID projectId, UUID repositoryId, UUID ticketId, String search) {
        QaFilter filter = normalize(projectId, repositoryId, ticketId, search, 0, DEFAULT_PAGE_SIZE);
        List<QaDashboardModels.AcCoverageRow> rows = repository.findAllForExport(filter);
        StringBuilder csv = new StringBuilder();
        csv.append("ac_id,ticket_key,status,blackbox,gap\n");
        for (QaDashboardModels.AcCoverageRow row : rows) {
            csv.append(csvEscape(row.acId()))
                    .append(',')
                    .append(csvEscape(row.ticketKey()))
                    .append(',')
                    .append(csvEscape(row.status()))
                    .append(',')
                    .append(csvEscape(row.blackbox()))
                    .append(',')
                    .append(csvEscape(row.gap()))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ── Validation / normalization ────────────────────────────────────────────

    QaFilter normalize(UUID projectId, UUID repositoryId,
            UUID ticketId, String search,
            int page, int size) {
        int clampedSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int clampedPage = Math.max(page, 0);

        return new QaFilter(projectId, repositoryId, ticketId, cleanSearch(search), clampedPage, clampedSize);
    }

    private String cleanSearch(String search) {
        String cleanSearch = search == null ? null : search.trim();
        if (cleanSearch != null && cleanSearch.length() > MAX_SEARCH_LENGTH) {
            throw new IllegalArgumentException("search exceeds " + MAX_SEARCH_LENGTH + " characters");
        }
        return (cleanSearch == null || cleanSearch.isBlank()) ? null : cleanSearch;
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String csvEscape(String value) {
        if (value == null)
            return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
