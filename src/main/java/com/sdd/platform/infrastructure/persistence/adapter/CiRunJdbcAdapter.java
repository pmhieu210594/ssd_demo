package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.CiRunRepositoryPort;
import com.sdd.platform.application.usecase.ingestion.CiRunModels.CiRunMetadataView;
import com.sdd.platform.application.usecase.ingestion.CiRunModels.ConnectorScope;
import com.sdd.platform.application.usecase.ingestion.CiRunModels.PullRequestScope;
import com.sdd.platform.domain.model.CiJob;
import com.sdd.platform.domain.model.CiRun;
import com.sdd.platform.domain.model.ConnectorRun;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CiRunJdbcAdapter implements CiRunRepositoryPort {

    private static final String SYSTEM_USER = "SYSTEM";

    private final NamedParameterJdbcTemplate jdbc;

    public CiRunJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ConnectorScope> findConnectorByType(String connectorType) {
        var rows = jdbc.query("""
                SELECT connector_id, connector_type, connector_name
                FROM tbl_source_connector
                WHERE connector_type = :connectorType
                  AND enabled = TRUE
                ORDER BY created_at ASC
                LIMIT 1
                """,
                new MapSqlParameterSource("connectorType", connectorType),
                (rs, rowNum) -> new ConnectorScope(
                        rs.getObject("connector_id", UUID.class),
                        rs.getString("connector_type"),
                        rs.getString("connector_name")));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<PullRequestScope> findPullRequestByRepositoryAndExternalNumber(UUID repositoryId, int externalPrNumber) {
        var rows = jdbc.query("""
                SELECT pr_id, ticket_id, external_pr_number, source_branch
                FROM tbl_fact_pull_request
                WHERE repository_id = :repositoryId
                  AND (
                      external_pr_number = :externalPrNumber
                      OR external_pr_id = CAST(:externalPrNumber AS VARCHAR(100))
                  )
                ORDER BY
                    CASE WHEN external_pr_number IS NULL THEN 1 ELSE 0 END,
                    collected_at DESC,
                    created_at DESC
                LIMIT 1
                """,
                new MapSqlParameterSource()
                        .addValue("repositoryId", repositoryId)
                        .addValue("externalPrNumber", externalPrNumber),
                (rs, rowNum) -> new PullRequestScope(
                        rs.getObject("pr_id", UUID.class),
                        rs.getObject("ticket_id", UUID.class),
                        safeInteger(rs.getString("external_pr_number")),
                        rs.getString("source_branch")));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<PullRequestScope> findPullRequestByRepositoryAndBranch(UUID repositoryId, String sourceBranch) {
        if (sourceBranch == null || sourceBranch.isBlank()) {
            return Optional.empty();
        }
        var rows = jdbc.query("""
                SELECT pr_id, ticket_id, external_pr_number, source_branch
                FROM tbl_fact_pull_request
                WHERE repository_id = :repositoryId
                  AND source_branch = :sourceBranch
                ORDER BY collected_at DESC, created_at DESC
                LIMIT 1
                """,
                new MapSqlParameterSource()
                        .addValue("repositoryId", repositoryId)
                        .addValue("sourceBranch", sourceBranch.trim()),
                (rs, rowNum) -> new PullRequestScope(
                        rs.getObject("pr_id", UUID.class),
                        rs.getObject("ticket_id", UUID.class),
                        safeInteger(rs.getString("external_pr_number")),
                        rs.getString("source_branch")));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<PullRequestScope> findPullRequestByRepositoryAndCommit(UUID repositoryId, String commitSha) {
        if (commitSha == null || commitSha.isBlank()) {
            return Optional.empty();
        }
        var rows = jdbc.query("""
                SELECT pr.pr_id, pr.ticket_id, pr.external_pr_number, pr.source_branch
                FROM tbl_fact_pull_request pr
                JOIN tbl_fact_pull_request_commit prc ON prc.pr_id = pr.pr_id
                JOIN tbl_fact_commit c ON c.commit_id = prc.commit_id
                WHERE pr.repository_id = :repositoryId
                  AND c.commit_hash = :commitSha
                ORDER BY pr.collected_at DESC, pr.created_at DESC
                LIMIT 1
                """,
                new MapSqlParameterSource()
                        .addValue("repositoryId", repositoryId)
                        .addValue("commitSha", commitSha.trim()),
                (rs, rowNum) -> new PullRequestScope(
                        rs.getObject("pr_id", UUID.class),
                        rs.getObject("ticket_id", UUID.class),
                        safeInteger(rs.getString("external_pr_number")),
                        rs.getString("source_branch")));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<PullRequestScope> findLatestPullRequestByTicket(UUID ticketId) {
        if (ticketId == null) {
            return Optional.empty();
        }
        var rows = jdbc.query("""
                SELECT pr_id, ticket_id, external_pr_number, source_branch
                FROM tbl_fact_pull_request
                WHERE ticket_id = :ticketId
                ORDER BY collected_at DESC, created_at DESC
                LIMIT 1
                """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> new PullRequestScope(
                        rs.getObject("pr_id", UUID.class),
                        rs.getObject("ticket_id", UUID.class),
                        safeInteger(rs.getString("external_pr_number")),
                        rs.getString("source_branch")));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<UUID> findTicketIdByProjectIdAndExternalKey(UUID projectId, String externalTicketKey) {
        var rows = jdbc.query("""
                SELECT ticket_id
                FROM tbl_dim_ticket
                WHERE project_id = :projectId
                  AND UPPER(TRIM(external_ticket_key)) = UPPER(TRIM(:externalTicketKey))
                ORDER BY created_at DESC
                LIMIT 1
                """,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("externalTicketKey", canonicalTicketKey(externalTicketKey)),
                (rs, rowNum) -> rs.getObject("ticket_id", UUID.class));
        return rows.stream().findFirst();
    }

    private static String canonicalTicketKey(String externalTicketKey) {
        return externalTicketKey == null ? null : externalTicketKey.trim().toUpperCase();
    }

    @Override
    public Optional<String> findTicketKeyByPullRequestId(UUID pullRequestId) {
        if (pullRequestId == null) {
            return Optional.empty();
        }
        var rows = jdbc.query("""
                SELECT file_path
                FROM tbl_fact_pull_request_changed_file
                WHERE pr_id = :pullRequestId
                ORDER BY collected_at ASC, created_at ASC
                LIMIT 1
                """,
                new MapSqlParameterSource("pullRequestId", pullRequestId),
                (rs, rowNum) -> extractTicketKeyFromPath(rs.getString("file_path")));
        return rows.stream().filter(key -> key != null && !key.isBlank()).findFirst();
    }

    @Override
    public Optional<UUID> findCiRunIdByIdentity(String ciProvider, UUID repositoryId, String externalRunId) {
        var rows = jdbc.query("""
                SELECT ci_run_id
                FROM tbl_fact_ci_run
                WHERE ci_provider = :ciProvider
                  AND repository_id = :repositoryId
                  AND COALESCE(external_run_id, external_ci_run_id) = :externalRunId
                LIMIT 1
                """,
                new MapSqlParameterSource()
                        .addValue("ciProvider", ciProvider)
                        .addValue("repositoryId", repositoryId)
                        .addValue("externalRunId", externalRunId),
                (rs, rowNum) -> rs.getObject("ci_run_id", UUID.class));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<CiRunMetadataView> findLatestCiRunByRepositoryAndTicket(UUID repositoryId, UUID ticketId) {
        if (repositoryId == null || ticketId == null) {
            return Optional.empty();
        }
        var rows = jdbc.query("""
                SELECT
                    c.ci_run_id,
                    c.repository_id,
                    COALESCE(r.repo_name_masked, c.repository_id::text) AS repository_name_masked,
                    COALESCE(c.project_id, r.project_id) AS project_id,
                    c.ci_provider,
                    c.workflow_name,
                    COALESCE(c.external_run_id, c.external_ci_run_id) AS external_run_id,
                    c.ci_url,
                    c.status::text AS status,
                    c.started_at,
                    COALESCE(c.completed_at, c.finished_at) AS completed_at,
                    COALESCE(c.updated_at, c.created_at, c.collected_at, now()) AS collected_at
                FROM tbl_fact_ci_run c
                LEFT JOIN tbl_dim_repository r ON r.repository_id = c.repository_id
                WHERE c.repository_id = :repositoryId
                  AND c.ticket_id = :ticketId
                ORDER BY COALESCE(c.updated_at, c.created_at, c.collected_at, now()) DESC, c.ci_run_id DESC
                LIMIT 1
                """,
                new MapSqlParameterSource()
                        .addValue("repositoryId", repositoryId)
                        .addValue("ticketId", ticketId),
                this::mapCiRunMetadata);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<CiRunMetadataView> findFirstCiRunByTicketId(UUID ticketId) {
        if (ticketId == null) {
            return Optional.empty();
        }
        var rows = jdbc.query("""
                SELECT
                    c.ci_run_id,
                    c.repository_id,
                    COALESCE(r.repo_name_masked, c.repository_id::text) AS repository_name_masked,
                    COALESCE(c.project_id, r.project_id) AS project_id,
                    c.ci_provider,
                    c.workflow_name,
                    COALESCE(c.external_run_id, c.external_ci_run_id) AS external_run_id,
                    c.ci_url,
                    c.status::text AS status,
                    c.started_at,
                    COALESCE(c.completed_at, c.finished_at) AS completed_at,
                    COALESCE(c.updated_at, c.created_at, c.collected_at, now()) AS collected_at
                FROM tbl_fact_ci_run c
                LEFT JOIN tbl_dim_repository r ON r.repository_id = c.repository_id
                WHERE c.ticket_id = :ticketId
                ORDER BY c.started_at ASC NULLS LAST, c.ci_run_id ASC
                LIMIT 1
                """,
                new MapSqlParameterSource("ticketId", ticketId),
                this::mapCiRunMetadata);
        return rows.stream().findFirst();
    }

    @Override
    public List<CiRunMetadataView> findRecentCiRuns(int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        return jdbc.query("""
                SELECT
                    c.ci_run_id,
                    c.repository_id,
                    COALESCE(r.repo_name_masked, c.repository_id::text) AS repository_name_masked,
                    COALESCE(c.project_id, r.project_id) AS project_id,
                    c.ci_provider,
                    c.workflow_name,
                    COALESCE(c.external_run_id, c.external_ci_run_id) AS external_run_id,
                    c.ci_url,
                    c.status::text AS status,
                    c.started_at,
                    COALESCE(c.completed_at, c.finished_at) AS completed_at,
                    COALESCE(c.updated_at, c.created_at, c.collected_at, now()) AS collected_at
                FROM tbl_fact_ci_run c
                LEFT JOIN tbl_dim_repository r ON r.repository_id = c.repository_id
                ORDER BY COALESCE(c.updated_at, c.created_at, c.collected_at, now()) DESC, c.ci_run_id DESC
                LIMIT :limit
                """,
                new MapSqlParameterSource("limit", normalizedLimit),
                this::mapCiRunMetadata);
    }

    @Override
    public CiRun insertCiRun(CiRun run) {
        UUID id = run.getId() == null ? UUID.randomUUID() : run.getId();
        run.setId(id);
        UUID actualId = jdbc.queryForObject("""
                INSERT INTO tbl_fact_ci_run (
                    ci_run_id,
                    project_id,
                    repository_id,
                    ticket_id,
                    pull_request_id,
                    connector_run_id,
                    ci_provider,
                    external_ci_run_id,
                    external_run_id,
                    workflow_name,
                    status,
                    started_at,
                    completed_at,
                    ci_url,
                    created_at,
                    updated_at
                )
                VALUES (
                    :ciRunId,
                    :projectId,
                    :repositoryId,
                    :ticketId,
                    :pullRequestId,
                    :connectorRunId,
                    :ciProvider,
                    :externalRunId,
                    :externalRunId,
                    :workflowName,
                    :status,
                    :startedAt,
                    :completedAt,
                    :ciUrl,
                    COALESCE(:createdAt, now()),
                    COALESCE(:updatedAt, now())
                )
                ON CONFLICT (ci_provider, repository_id, external_run_id)
                DO UPDATE SET
                    project_id = EXCLUDED.project_id,
                    ticket_id = EXCLUDED.ticket_id,
                    pull_request_id = EXCLUDED.pull_request_id,
                    connector_run_id = EXCLUDED.connector_run_id,
                    workflow_name = EXCLUDED.workflow_name,
                    ci_url = COALESCE(EXCLUDED.ci_url, tbl_fact_ci_run.ci_url),
                    started_at = LEAST(tbl_fact_ci_run.started_at, EXCLUDED.started_at),
                    updated_at = COALESCE(EXCLUDED.updated_at, now())
                RETURNING ci_run_id
                """,
                paramsForCiRun(run),
                UUID.class);
        run.setId(actualId);
        return run;
    }

    @Override
    public CiRun updateCiRun(CiRun run) {
        jdbc.update("""
                UPDATE tbl_fact_ci_run
                SET project_id = :projectId,
                    repository_id = :repositoryId,
                    ticket_id = :ticketId,
                    pull_request_id = :pullRequestId,
                    connector_run_id = :connectorRunId,
                    ci_provider = :ciProvider,
                    external_ci_run_id = :externalRunId,
                    external_run_id = :externalRunId,
                    workflow_name = :workflowName,
                    status = :status,
                    started_at = :startedAt,
                    completed_at = :completedAt,
                    ci_url = :ciUrl,
                    updated_at = COALESCE(:updatedAt, now())
                WHERE ci_run_id = :ciRunId
                """,
                paramsForCiRun(run));
        return run;
    }

    @Override
    public ConnectorRun insertConnectorRun(ConnectorRun run) {
        UUID id = jdbc.queryForObject("""
                INSERT INTO tbl_connector_run (
                    connector_id,
                    provider,
                    repository_id,
                    started_at,
                    finished_at,
                    status,
                    records_read,
                    records_written,
                    records_received,
                    records_inserted,
                    records_updated,
                    records_skipped,
                    records_error,
                    error_message,
                    trace_id,
                    created_at,
                    updated_at,
                    created_by,
                    updated_by
                )
                VALUES (
                    :connectorId,
                    :provider,
                    :repositoryId,
                    :startedAt,
                    :finishedAt,
                    CAST(:status AS run_status),
                    :recordsReceived,
                    :recordsWritten,
                    :recordsReceived,
                    :recordsInserted,
                    :recordsUpdated,
                    :recordsSkipped,
                    :recordsError,
                    :errorMessage,
                    :traceId,
                    now(),
                    now(),
                    :createdBy,
                    :updatedBy
                )
                RETURNING connector_run_id
                """,
                paramsForConnectorRun(run),
                UUID.class);
        run.setId(id);
        return run;
    }

    @Override
    public ConnectorRun updateConnectorRun(ConnectorRun run) {
        jdbc.update("""
                UPDATE tbl_connector_run
                SET connector_id = :connectorId,
                    provider = :provider,
                    repository_id = :repositoryId,
                    started_at = :startedAt,
                    finished_at = :finishedAt,
                    status = CAST(:status AS run_status),
                    records_read = :recordsReceived,
                    records_written = :recordsWritten,
                    records_received = :recordsReceived,
                    records_inserted = :recordsInserted,
                    records_updated = :recordsUpdated,
                    records_skipped = :recordsSkipped,
                    records_error = :recordsError,
                    error_message = :errorMessage,
                    trace_id = :traceId,
                    updated_at = now(),
                    updated_by = :updatedBy
                WHERE connector_run_id = :connectorRunId
                """,
                paramsForConnectorRun(run));
        return run;
    }

    private MapSqlParameterSource paramsForCiRun(CiRun run) {
        return new MapSqlParameterSource()
                .addValue("ciRunId", run.getId())
                .addValue("projectId", run.getProjectId())
                .addValue("repositoryId", run.getRepositoryId())
                .addValue("ticketId", run.getTicketId())
                .addValue("pullRequestId", run.getPullRequestId())
                .addValue("connectorRunId", run.getConnectorRunId())
                .addValue("ciProvider", run.getCiProvider())
                .addValue("externalRunId", run.getExternalRunId())
                .addValue("workflowName", run.getWorkflowName())
                .addValue("status", run.getStatus())
                .addValue("startedAt", run.getStartedAt())
                .addValue("completedAt", run.getCompletedAt())
                .addValue("ciUrl", run.getCiUrl())
                .addValue("createdAt", run.getCreatedAt())
                .addValue("updatedAt", run.getUpdatedAt());
    }

    private MapSqlParameterSource paramsForConnectorRun(ConnectorRun run) {
        int recordsReceived = valueOrZero(run.getRecordsReceived());
        int recordsInserted = valueOrZero(run.getRecordsInserted());
        int recordsUpdated = valueOrZero(run.getRecordsUpdated());
        int recordsSkipped = valueOrZero(run.getRecordsSkipped());
        int recordsError = valueOrZero(run.getRecordsError());
        return new MapSqlParameterSource()
                .addValue("connectorRunId", run.getId())
                .addValue("connectorId", run.getConnectorId())
                .addValue("provider", run.getProvider())
                .addValue("repositoryId", run.getRepositoryId())
                .addValue("startedAt", run.getStartedAt())
                .addValue("finishedAt", run.getFinishedAt())
                .addValue("status", run.getStatus() == null ? null : run.getStatus().name())
                .addValue("recordsReceived", recordsReceived)
                .addValue("recordsWritten", recordsInserted + recordsUpdated)
                .addValue("recordsInserted", recordsInserted)
                .addValue("recordsUpdated", recordsUpdated)
                .addValue("recordsSkipped", recordsSkipped)
                .addValue("recordsError", recordsError)
                .addValue("errorMessage", run.getErrorMessage())
                .addValue("traceId", run.getTraceId())
                .addValue("createdBy", SYSTEM_USER)
                .addValue("updatedBy", SYSTEM_USER);
    }

    private static Integer safeInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String extractTicketKeyFromPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        int idx = path.indexOf("/changes/");
        int start;
        if (idx >= 0) {
            start = idx + "/changes/".length();
        } else if (path.startsWith("changes/")) {
            start = "changes/".length();
        } else {
            return null;
        }
        int slashIndex = path.indexOf('/', start);
        if (slashIndex <= start) {
            return null;
        }
        String ticket = path.substring(start, slashIndex).trim();
        return ticket.isBlank() ? null : ticket;
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private CiRunMetadataView mapCiRunMetadata(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new CiRunMetadataView(
                rs.getObject("ci_run_id", UUID.class),
                rs.getObject("repository_id", UUID.class),
                rs.getString("repository_name_masked"),
                rs.getObject("project_id", UUID.class),
                rs.getString("ci_provider"),
                rs.getString("workflow_name"),
                rs.getString("external_run_id"),
                rs.getString("ci_url"),
                rs.getString("status"),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class),
                rs.getObject("collected_at", OffsetDateTime.class)
        );
    }

    @Override
    public CiJob upsertCiJob(CiJob job) {
        UUID id = job.getId() == null ? UUID.randomUUID() : job.getId();
        UUID actualId = jdbc.queryForObject("""
                INSERT INTO tbl_fact_ci_job (
                    ci_job_id, ci_run_id, external_job_id, job_name,
                    status, started_at, finished_at
                )
                VALUES (
                    :ciJobId, :ciRunId, :externalJobId, :jobName,
                    CAST(:status AS run_status), :startedAt, :finishedAt
                )
                ON CONFLICT (ci_run_id, external_job_id)
                DO UPDATE SET
                    job_name       = EXCLUDED.job_name,
                    status         = EXCLUDED.status,
                    started_at     = EXCLUDED.started_at,
                    finished_at    = EXCLUDED.finished_at
                RETURNING ci_job_id
                """,
                new MapSqlParameterSource()
                        .addValue("ciJobId", id)
                        .addValue("ciRunId", job.getCiRunId())
                        .addValue("externalJobId", job.getExternalJobId())
                        .addValue("jobName", job.getJobName())
                        .addValue("status", job.getStatus())
                        .addValue("startedAt", job.getStartedAt())
                        .addValue("finishedAt", job.getFinishedAt()),
                UUID.class);
        job.setId(actualId);
        return job;
    }

    @Override
    public String computeAggregateRunStatus(UUID ciRunId) {
        if (ciRunId == null) {
            return "UNKNOWN";
        }
        var rows = jdbc.query("""
                SELECT
                    CASE
                        WHEN COUNT(*) = 0 THEN 'IN_PROGRESS'
                        WHEN COUNT(*) FILTER (WHERE status::text IN ('FAILURE', 'FAILED')) > 0 THEN 'FAILURE'
                        WHEN COUNT(*) FILTER (WHERE status::text NOT IN ('SUCCESS', 'SKIPPED')) > 0 THEN 'IN_PROGRESS'
                        ELSE 'SUCCESS'
                    END AS aggregate_status
                FROM tbl_fact_ci_job
                WHERE ci_run_id = :ciRunId
                """,
                new MapSqlParameterSource("ciRunId", ciRunId),
                (rs, rowNum) -> rs.getString("aggregate_status"));
        return rows.stream().findFirst().orElse("UNKNOWN");
    }

    @Override
    public void updateCiRunStatus(UUID ciRunId, String status) {
        if (ciRunId == null || status == null) {
            return;
        }
        jdbc.update("""
                UPDATE tbl_fact_ci_run
                SET status = CAST(:status AS run_status), updated_at = now()
                WHERE ci_run_id = :ciRunId
                """,
                new MapSqlParameterSource()
                        .addValue("ciRunId", ciRunId)
                        .addValue("status", status));
    }
}
