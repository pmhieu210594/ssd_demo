package com.sdd.platform.application.usecase.devdashboard;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.DevDashboardRepositoryPort;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AuthUserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class DevDashboardService {

    private static final int MAX_SEARCH_LENGTH = 255;
    private static final Set<String> VALID_CI_STATUS = Set.of("PASS", "FAIL", "FAILURE");
    private static final Set<String> VALID_REVIEW_STATUS = Set.of("OPEN", "RESOLVED");
    private static final Set<String> VALID_PARSER_STATUS = Set.of("SUCCESS", "WARNING", "ERROR");

    private final DevDashboardRepositoryPort repository;

    public DevDashboardService(DevDashboardRepositoryPort repository) {
        this.repository = repository;
    }

    /**
     * Route-entry gate: does the caller hold the DEV role on *any* project (or
     * ADMIN)?
     */
    public void requireAnyAccess(AuthUserContext caller) {
        if (caller == null || !repository.hasDashboardAccess(caller)) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    @Transactional(readOnly = true)
    public DevDashboardModels.DevDashboardSummary summary(
            UUID projectId,
            UUID repositoryId,
            String ciStatus,
            String reviewStatus,
            String parserStatus,
            String search,
            AuthUserContext caller) {
        requireDevAccess(caller, projectId);
        return repository
                .findSummary(normalize(projectId, repositoryId, ciStatus, reviewStatus, parserStatus, search, 1, 20));
    }

    @Transactional(readOnly = true)
    public DevDashboardModels.DevDashboardPage tickets(
            UUID projectId,
            UUID repositoryId,
            String ciStatus,
            String reviewStatus,
            String parserStatus,
            String search,
            int page,
            int size,
            AuthUserContext caller) {
        requireDevAccess(caller, projectId);
        return repository.findTickets(
                normalize(projectId, repositoryId, ciStatus, reviewStatus, parserStatus, search, page, size));
    }

    @Transactional(readOnly = true)
    public DevDashboardModels.DevTicketDetail detail(UUID ticketId, AuthUserContext caller) {
        DevDashboardModels.DevTicketDetail detail = repository.findDetail(ticketId)
                .orElseThrow(() -> new NotFoundException("Pages.DevDashboard.NotFound"));
        requireDevAccess(caller, detail.row().projectId());
        return detail;
    }

    @Transactional(readOnly = true)
    public DevDashboardModels.DevDashboardOptions options(UUID projectId, AuthUserContext caller) {
        requireAuthenticated(caller);
        return repository.findOptions(projectId, caller);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(
            UUID projectId,
            UUID repositoryId,
            String ciStatus,
            String reviewStatus,
            String parserStatus,
            String search,
            AuthUserContext caller) {
        requireDevAccess(caller, projectId);
        List<DevDashboardModels.DevTicketRow> rows = repository.findAllTickets(
                normalize(projectId, repositoryId, ciStatus, reviewStatus, parserStatus, search, 1, 1000));
        StringBuilder csv = new StringBuilder();
        csv.append(
                "ticket_id,external_ticket_key,title,ticket_status,ci_fail_count,latest_ci_status,review_comment_count,parser_error_flag,open_finding_count,age_days\n");
        for (DevDashboardModels.DevTicketRow row : rows) {
            csv.append(csvField(row.ticketId()))
                    .append(',').append(csvField(row.externalTicketKey()))
                    .append(',').append(csvField(row.title()))
                    .append(',').append(csvField(row.ticketStatus()))
                    .append(',').append(row.ciFailCount())
                    .append(',').append(csvField(row.latestCiStatus()))
                    .append(',').append(row.reviewCommentCount())
                    .append(',').append(row.parserErrorFlag())
                    .append(',').append(row.openFindingCount())
                    .append(',').append(row.ageDays())
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private DevDashboardModels.DevDashboardFilter normalize(
            UUID projectId,
            UUID repositoryId,
            String ciStatus,
            String reviewStatus,
            String parserStatus,
            String search,
            int page,
            int size) {
        String normalizedSearch = trimToNull(search);
        if (normalizedSearch != null && normalizedSearch.length() > MAX_SEARCH_LENGTH) {
            throw new IllegalArgumentException("Pages.DevDashboard.Search.TooLong");
        }

        String normalizedCiStatus = null;
        if (ciStatus != null && !ciStatus.isBlank()) {
            normalizedCiStatus = ciStatus.trim().toUpperCase(Locale.ROOT);
            if (!VALID_CI_STATUS.contains(normalizedCiStatus)) {
                throw new IllegalArgumentException("Pages.DevDashboard.CiStatus.Invalid");
            }
        }

        String normalizedReviewStatus = null;
        if (reviewStatus != null && !reviewStatus.isBlank()) {
            normalizedReviewStatus = reviewStatus.trim().toUpperCase(Locale.ROOT);
            if (!VALID_REVIEW_STATUS.contains(normalizedReviewStatus)) {
                throw new IllegalArgumentException("Pages.DevDashboard.ReviewStatus.Invalid");
            }
        }

        String normalizedParserStatus = null;
        if (parserStatus != null && !parserStatus.isBlank()) {
            normalizedParserStatus = parserStatus.trim().toUpperCase(Locale.ROOT);
            if (!VALID_PARSER_STATUS.contains(normalizedParserStatus)) {
                throw new IllegalArgumentException("Pages.DevDashboard.ParserStatus.Invalid");
            }
        }

        return new DevDashboardModels.DevDashboardFilter(
                projectId,
                repositoryId,
                normalizedCiStatus,
                normalizedReviewStatus,
                normalizedParserStatus,
                normalizedSearch,
                Math.max(page, 1),
                Math.max(size, 1));
    }

    private void requireAuthenticated(AuthUserContext caller) {
        if (caller == null) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    /**
     * Gates access to a specific project's DEV data. ADMIN system role
     * always passes; otherwise the caller must hold the DEV project role for
     * the given projectId — holding DEV on a *different* project is not
     * sufficient.
     */
    public void requireDevAccess(AuthUserContext caller, UUID projectId) {
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
        if (!"DEV".equalsIgnoreCase(projectRole)) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    private static String trimToNull(String value) {
        if (value == null)
            return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String csvField(Object value) {
        if (value == null)
            return "";
        String text = String.valueOf(value).replace("\"", "\"\"");
        if (text.contains(",") || text.contains("\n") || text.contains("\"")) {
            return "\"" + text + "\"";
        }
        return text;
    }
}
