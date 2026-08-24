package com.sdd.platform.application.usecase.dataopsdashboard;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.DataOpsDashboardRepositoryPort;
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
public class DataOpsDashboardService {

    private static final int MAX_SEARCH_LENGTH = 255;
    private static final Set<String> VALID_PARSER_STATUS = Set.of("SUCCESS", "WARNING", "ERROR");

    private final DataOpsDashboardRepositoryPort repository;

    public DataOpsDashboardService(DataOpsDashboardRepositoryPort repository) {
        this.repository = repository;
    }

    /**
     * Route-entry gate: does the caller hold the DATA_OPS role on *any* project (or
     * ADMIN)?
     */
    public void requireAnyAccess(AuthUserContext caller) {
        if (caller == null || !repository.hasDashboardAccess(caller)) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    @Transactional(readOnly = true)
    public DataOpsDashboardModels.DataOpsDashboardSummary summary(
            UUID projectId,
            UUID repositoryId,
            String connectorName,
            String parserStatus,
            String search,
            AuthUserContext caller) {
        requireDataOpsAccess(caller, projectId);
        return repository.findSummary(normalize(projectId, repositoryId, connectorName, parserStatus, search, 1, 20));
    }

    @Transactional(readOnly = true)
    public DataOpsDashboardModels.DataOpsDashboardPage connectors(
            UUID projectId,
            UUID repositoryId,
            String connectorName,
            String parserStatus,
            String search,
            int page,
            int size,
            AuthUserContext caller) {
        requireDataOpsAccess(caller, projectId);
        return repository
                .findConnectors(normalize(projectId, repositoryId, connectorName, parserStatus, search, page, size));
    }

    @Transactional(readOnly = true)
    public DataOpsDashboardModels.DataOpsConnectorDetail connectorDetail(UUID connectorId, AuthUserContext caller) {
        if (caller == null) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
        DataOpsDashboardModels.DataOpsConnectorDetail detail = repository.findConnectorDetail(connectorId)
                .orElseThrow(() -> new NotFoundException("Pages.DataOpsDashboard.NotFound"));
        requireDataOpsAccess(caller, detail.row().projectId());
        return detail;
    }

    @Transactional(readOnly = true)
    public List<DataOpsDashboardModels.DataOpsMissingEvidenceItem> repositoryMissingEvidence(
            UUID repositoryId,
            AuthUserContext caller) {
        requireDataOpsAccess(caller, null);
        return repository.findRepositoryMissingEvidence(repositoryId);
    }

    @Transactional(readOnly = true)
    public DataOpsDashboardModels.DataOpsDashboardOptions options(
            UUID projectId,
            UUID repositoryId,
            AuthUserContext caller) {
        requireAuthenticated(caller);
        return repository.findOptions(projectId, repositoryId, caller);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(
            UUID projectId,
            UUID repositoryId,
            String connectorName,
            String parserStatus,
            String search,
            AuthUserContext caller) {
        requireDataOpsAccess(caller, projectId);
        DataOpsDashboardModels.DataOpsDashboardPage page = repository.findConnectors(
                normalize(projectId, repositoryId, connectorName, parserStatus, search, 1, 100));
        StringBuilder csv = new StringBuilder();
        csv.append(
                "connector_id,project_alias,repository_name,connector_name,connector_type,latest_run_status,latest_run_at,failed_run_count,parse_error_count,missing_evidence_count,freshness_delay_minutes\n");
        for (DataOpsDashboardModels.DataOpsConnectorRow row : page.items()) {
            csv.append(csv(row.connectorId()))
                    .append(',')
                    .append(csv(row.projectAlias()))
                    .append(',')
                    .append(csv(row.repositoryName()))
                    .append(',')
                    .append(csv(row.connectorName()))
                    .append(',')
                    .append(csv(row.connectorType()))
                    .append(',')
                    .append(csv(row.latestRunStatus()))
                    .append(',')
                    .append(csv(row.latestRunAt()))
                    .append(',')
                    .append(row.failedRunCount())
                    .append(',')
                    .append(row.parseErrorCount())
                    .append(',')
                    .append(row.missingEvidenceCount())
                    .append(',')
                    .append(row.freshnessDelayMinutes() == null ? "" : row.freshnessDelayMinutes())
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private DataOpsDashboardModels.DataOpsDashboardFilter normalize(
            UUID projectId,
            UUID repositoryId,
            String connectorName,
            String parserStatus,
            String search,
            int page,
            int size) {
        String normalizedSearch = trimToNull(search);
        if (normalizedSearch != null && normalizedSearch.length() > MAX_SEARCH_LENGTH) {
            throw new IllegalArgumentException("Pages.DataOpsDashboard.Search.TooLong");
        }

        String normalizedParserStatus = null;
        if (parserStatus != null && !parserStatus.isBlank()) {
            normalizedParserStatus = parserStatus.trim().toUpperCase(Locale.ROOT);
            if (!VALID_PARSER_STATUS.contains(normalizedParserStatus)) {
                throw new IllegalArgumentException("Pages.DataOpsDashboard.ParserStatus.Invalid");
            }
        }

        return new DataOpsDashboardModels.DataOpsDashboardFilter(
                projectId,
                repositoryId,
                trimToNull(connectorName),
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
     * Gates access to a specific project's DATA_OPS data. ADMIN system
     * role always passes; otherwise the caller must hold the DATA_OPS project
     * role for the given projectId — holding DATA_OPS on a *different* project
     * is not sufficient. A null projectId (e.g. resolving evidence by
     * repository only) falls back to requiring the system-level role.
     */
    public void requireDataOpsAccess(AuthUserContext caller, UUID projectId) {
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
        if (!"DATA_OPS".equalsIgnoreCase(projectRole)) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    private static String trimToNull(String value) {
        if (value == null)
            return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
