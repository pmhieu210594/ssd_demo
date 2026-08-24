package com.sdd.platform.application.usecase.securitydashboard;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.SecurityDashboardRepositoryPort;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityFilter;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityDashboardOptions;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecuritySummaryModel;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityTicketDetail;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityTicketPage;
import com.sdd.platform.domain.model.AuthUserContext;
import com.sdd.platform.domain.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class SecurityDashboardService {

    private static final int MAX_SEARCH_LENGTH = 200;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> SAFETY_STATUSES = Set.of("READY", "WARNING", "MISSING");
    private static final Set<String> SECRET_SCAN_STATUSES = Set.of("PASS", "FAIL");
    private static final Set<String> SAST_STATUSES = Set.of("PASS", "WARNING", "FAIL");
    private static final Set<String> EXCEPTION_STATUSES = Set.of("OPEN", "CLOSED", "EXPIRED");

    private final SecurityDashboardRepositoryPort repository;

    public SecurityDashboardService(SecurityDashboardRepositoryPort repository) {
        this.repository = repository;
    }

    /**
     * Route-entry gate: does the caller hold the SECURITY role on *any* project (or
     * ADMIN)?
     */
    public void requireAnyAccess(AuthUserContext caller) {
        if (caller == null || !repository.hasDashboardAccess(caller)) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    /**
     * Gates access to a specific project's SECURITY data. ADMIN
     * system role always passes; otherwise the caller must hold the SECURITY
     * project role for the given projectId — holding SECURITY on a *different*
     * project is not sufficient. A null projectId (e.g. ticket-detail lookups
     * where the project isn't resolved) falls back to requiring the
     * system-level role.
     */
    public void requireSecurityAccess(AuthUserContext caller, UUID projectId) {
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
        if (!"SECURITY".equalsIgnoreCase(projectRole)) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    @Transactional(readOnly = true)
    public SecuritySummaryModel getSummary(UUID projectId, UUID repositoryId, String search,
            String safetyStatus, String secretScanStatus,
            String sastStatus, String exceptionStatus) {
        SecurityFilter filter = normalize(projectId, repositoryId, search,
                safetyStatus, secretScanStatus, sastStatus, exceptionStatus, 0, DEFAULT_PAGE_SIZE);
        return new SecuritySummaryModel(
                repository.findSafetyPackCounts(filter),
                repository.findSecretScanCounts(filter),
                repository.findSastScaCounts(filter),
                repository.findExceptionCounts(filter),
                OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public SecurityTicketPage getTickets(UUID projectId, UUID repositoryId, String search,
            String safetyStatus, String secretScanStatus,
            String sastStatus, String exceptionStatus,
            int page, int size) {
        SecurityFilter filter = normalize(projectId, repositoryId, search,
                safetyStatus, secretScanStatus, sastStatus, exceptionStatus, page, size);
        return repository.findTicketRows(filter);
    }

    @Transactional(readOnly = true)
    public UUID getProjectIdForTicket(UUID ticketId) {
        return repository.findProjectIdByTicketId(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));
    }

    @Transactional(readOnly = true)
    public SecurityTicketDetail getTicketDetail(UUID ticketId) {
        return repository.findTicketDetail(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));
    }

    @Transactional(readOnly = true)
    public SecurityDashboardOptions getOptions(UUID projectId, UUID repositoryId, AuthUserContext caller) {
        if (caller == null) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
        return repository.findOptions(projectId, repositoryId, caller);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(
            UUID projectId,
            UUID repositoryId,
            String search,
            String safetyStatus,
            String secretScanStatus,
            String sastStatus,
            String exceptionStatus) {
        SecurityFilter filter = normalize(projectId, repositoryId, search,
                safetyStatus, secretScanStatus, sastStatus, exceptionStatus, 0, 100);
        SecurityTicketPage page = repository.findTicketRows(filter);
        StringBuilder csv = new StringBuilder();
        csv.append(
                "ticket_id,ticket_key,project_alias,repository_name,safety_status,secret_scan_status,sast_status,sca_status,exception_status,final_verdict\n");
        for (SecurityDashboardModels.SecurityTicketRow row : page.items()) {
            csv.append(csv(row.ticketId()))
                    .append(',')
                    .append(csv(row.ticketKey()))
                    .append(',')
                    .append(csv(row.projectAlias()))
                    .append(',')
                    .append(csv(row.repositoryName()))
                    .append(',')
                    .append(csv(row.safetyStatus()))
                    .append(',')
                    .append(csv(row.secretScanStatus()))
                    .append(',')
                    .append(csv(row.sastStatus()))
                    .append(',')
                    .append(csv(row.scaStatus()))
                    .append(',')
                    .append(csv(row.exceptionStatus()))
                    .append(',')
                    .append(csv(row.finalVerdict()))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ── Validation / normalization ─────────────────────────────────────────────

    SecurityFilter normalize(UUID projectId, UUID repositoryId, String search,
            String safetyStatus, String secretScanStatus,
            String sastStatus, String exceptionStatus,
            int page, int size) {
        String cleanSearch = search == null ? null : search.trim();
        if (cleanSearch != null && cleanSearch.length() > MAX_SEARCH_LENGTH) {
            throw new IllegalArgumentException("search exceeds " + MAX_SEARCH_LENGTH + " characters");
        }
        if (cleanSearch != null && cleanSearch.isBlank()) {
            cleanSearch = null;
        }

        String cleanSafety = validateEnum(safetyStatus, SAFETY_STATUSES, "safetyStatus");
        String cleanSecret = validateEnum(secretScanStatus, SECRET_SCAN_STATUSES, "secretScanStatus");
        String cleanSast = validateEnum(sastStatus, SAST_STATUSES, "sastStatus");
        String cleanException = validateEnum(exceptionStatus, EXCEPTION_STATUSES, "exceptionStatus");

        int clampedSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int clampedPage = Math.max(page, 0);

        return new SecurityFilter(projectId, repositoryId, cleanSearch,
                cleanSafety, cleanSecret, cleanSast, cleanException, clampedPage, clampedSize);
    }

    private String validateEnum(String value, Set<String> allowed, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String upper = value.trim().toUpperCase();
        if (!allowed.contains(upper)) {
            throw new IllegalArgumentException(fieldName + " must be one of " + allowed + ", got: " + value);
        }
        return upper;
    }

    private static String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).replace("\"", "\"\"");
        if (text.contains(",") || text.contains("\n") || text.contains("\"")) {
            return "\"" + text + "\"";
        }
        return text;
    }
}
