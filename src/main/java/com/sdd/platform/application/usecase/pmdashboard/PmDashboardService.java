package com.sdd.platform.application.usecase.pmdashboard;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.PmDashboardRepositoryPort;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AuthUserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PmDashboardService {

    private static final String PM_ROLE = "PM";
    private static final String ADMIN_ROLE = "ADMIN";

    private static final java.util.Set<String> VALID_SCORE_BANDS = java.util.Set.of(
            "EXCELLENT", "GOOD", "WARNING", "RISKY", "CRITICAL");
    private static final java.util.Set<String> VALID_RISK_LEVELS = java.util.Set.of(
            "HIGH", "MEDIUM", "LOW");

    private final PmDashboardRepositoryPort repository;

    public PmDashboardService(PmDashboardRepositoryPort repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PmDashboardModels.DashboardSummary summary(
            UUID projectId,
            String periodKey,
            UUID repositoryId,
            String phaseCode,
            String scoreBand,
            String riskLevel,
            String search,
            AuthUserContext caller
    ) {
        requirePm(caller);
        return repository.findSummary(normalize(projectId, periodKey, repositoryId, phaseCode, scoreBand, riskLevel, search, 1, 20));
    }

    @Transactional(readOnly = true)
    public PmDashboardModels.DashboardInsights insights(
            UUID projectId,
            String periodKey,
            UUID repositoryId,
            String phaseCode,
            String scoreBand,
            String riskLevel,
            String search,
            AuthUserContext caller
    ) {
        requirePm(caller);
        return repository.findInsights(normalize(projectId, periodKey, repositoryId, phaseCode, scoreBand, riskLevel, search, 1, 20));
    }

    @Transactional(readOnly = true)
    public PageResult<PmDashboardModels.DashboardTicketRow> tickets(
            UUID projectId,
            String periodKey,
            UUID repositoryId,
            String phaseCode,
            String scoreBand,
            String riskLevel,
            String search,
            int page,
            int size,
            AuthUserContext caller
    ) {
        requirePm(caller);
        return repository.findTickets(normalize(projectId, periodKey, repositoryId, phaseCode, scoreBand, riskLevel, search, page, size));
    }

    @Transactional(readOnly = true)
    public PmDashboardModels.DashboardTicketDetail detail(UUID ticketId, AuthUserContext caller) {
        requirePm(caller);
        return repository.findDetail(ticketId)
                .orElseThrow(() -> new NotFoundException("Pages.PmDashboard.NotFound"));
    }

    @Transactional
    public PmDashboardModels.DashboardRefreshResult refresh(AuthUserContext caller) {
        requirePm(caller);
        return repository.rebuildSnapshot();
    }

    @Transactional(readOnly = true)
    public PmDashboardModels.DashboardOptions options(UUID projectId, AuthUserContext caller) {
        requirePm(caller);
        return repository.findOptions(projectId);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(
            UUID projectId,
            String periodKey,
            UUID repositoryId,
            String phaseCode,
            String scoreBand,
            String riskLevel,
            String search,
            AuthUserContext caller
    ) {
        requirePm(caller);
        List<PmDashboardModels.DashboardTicketRow> rows = repository.findAllTickets(
                normalize(projectId, periodKey, repositoryId, phaseCode, scoreBand, riskLevel, search, 1, 100)
        );
        StringBuilder csv = new StringBuilder();
        csv.append("ticket_id,external_ticket_key,title,missing_evidence_count,risk_count,exception_count,evidence_quality_score,score_band,age_days,owner_display\n");
        for (PmDashboardModels.DashboardTicketRow row : rows) {
            csv.append(csv(row.ticketId()))
                    .append(',')
                    .append(csv(row.externalTicketKey()))
                    .append(',')
                    .append(csv(row.title()))
                    .append(',')
                    .append(row.missingEvidenceCount())
                    .append(',')
                    .append(row.riskCount())
                    .append(',')
                    .append(row.exceptionCount())
                    .append(',')
                    .append(row.evidenceQualityScore() == null ? "" : row.evidenceQualityScore())
                    .append(',')
                    .append(csv(row.scoreBand()))
                    .append(',')
                    .append(row.ageDays())
                    .append(',')
                    .append(csv(row.ownerDisplay()))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private PmDashboardModels.DashboardFilter normalize(
            UUID projectId,
            String periodKey,
            UUID repositoryId,
            String phaseCode,
            String scoreBand,
            String riskLevel,
            String search,
            int page,
            int size
    ) {
        String normalizedPeriod = trimToNull(periodKey);
        String normalizedPhaseCode = trimToNull(phaseCode);
        String normalizedScoreBand = trimToNull(scoreBand);
        String normalizedRiskLevel = trimToNull(riskLevel);
        String normalizedSearch = trimToNull(search);
        if (normalizedSearch != null && normalizedSearch.length() > 255) {
            throw new IllegalArgumentException("Pages.PmDashboard.Search.TooLong");
        }
        if (normalizedScoreBand != null) {
            normalizedScoreBand = normalizedScoreBand.toUpperCase(Locale.ROOT);
            if (!VALID_SCORE_BANDS.contains(normalizedScoreBand)) {
                throw new IllegalArgumentException("Pages.PmDashboard.ScoreBand.Invalid");
            }
        }
        if (normalizedRiskLevel != null) {
            normalizedRiskLevel = normalizedRiskLevel.toUpperCase(Locale.ROOT);
            if (!VALID_RISK_LEVELS.contains(normalizedRiskLevel)) {
                throw new IllegalArgumentException("Pages.PmDashboard.RiskLevel.Invalid");
            }
        }
        return new PmDashboardModels.DashboardFilter(
                projectId,
                normalizedPeriod,
                repositoryId,
                normalizedPhaseCode,
                normalizedScoreBand,
                normalizedRiskLevel,
                normalizedSearch,
                Math.max(page, 1),
                Math.max(size, 1)
        );
    }

    private void requirePm(AuthUserContext caller) {
        if (caller == null || caller.getRole() == null) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
        String role = caller.getRole().trim().toUpperCase(Locale.ROOT);
        if (!PM_ROLE.equals(role) && !ADMIN_ROLE.equals(role)) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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
