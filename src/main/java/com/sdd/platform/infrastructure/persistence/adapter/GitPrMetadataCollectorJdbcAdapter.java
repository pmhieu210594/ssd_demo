package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.GitPrMetadataCollectorPersistencePort;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.CollectorRun;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.CommitUpsert;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.PullRequestChangedFileUpsert;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.PullRequestUpsert;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.ReviewCommentUpsert;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.ReviewUpsert;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.TraceabilityLinkUpsert;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ConnectorScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.RepositoryScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.TicketScope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

@Repository
public class GitPrMetadataCollectorJdbcAdapter implements GitPrMetadataCollectorPersistencePort {

    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final NamedParameterJdbcTemplate jdbc;

    public GitPrMetadataCollectorJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RepositoryScope> findActiveRepository(UUID repositoryId) {
        var rows = jdbc.query("""
                SELECT repository_id, project_id, repo_name_masked, default_branch
                FROM tbl_dim_repository
                WHERE repository_id = :repositoryId
                  AND status = 'ACTIVE'::record_status
                """,
                new MapSqlParameterSource("repositoryId", repositoryId),
                (rs, rowNum) -> new RepositoryScope(
                        rs.getObject("repository_id", UUID.class),
                        rs.getObject("project_id", UUID.class),
                        rs.getString("repo_name_masked"),
                        rs.getString("default_branch")));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<TicketScope> findTicketByProjectIdAndExternalKey(UUID projectId, String externalTicketKey) {
        var rows = jdbc.query("""
                SELECT ticket_id, external_ticket_key
                FROM tbl_dim_ticket
                WHERE project_id = :projectId
                  AND UPPER(TRIM(external_ticket_key)) = UPPER(TRIM(:externalTicketKey))
                """,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("externalTicketKey", canonicalTicketKey(externalTicketKey)),
                (rs, rowNum) -> new TicketScope(
                        rs.getObject("ticket_id", UUID.class),
                        rs.getString("external_ticket_key")));
        return rows.stream().findFirst();
    }

    private static String canonicalTicketKey(String externalTicketKey) {
        return externalTicketKey == null ? null : externalTicketKey.trim().toUpperCase();
    }

    @Override
    public TicketScope upsertMinimalTicket(UUID projectId, String externalTicketKey, String title, String status, OffsetDateTime lastCommitAt, String createdBy) {
        UUID ticketId = jdbc.queryForObject("""
                INSERT INTO tbl_dim_ticket (
                    project_id,
                    external_ticket_key,
                    title,
                    status,
                    last_commit_at,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by
                )
                VALUES (
                    :projectId,
                    :externalTicketKey,
                    :title,
                    COALESCE(CAST(:status AS ticket_status), 'OPEN'::ticket_status),
                    :lastCommitAt,
                    now(),
                    COALESCE(NULLIF(TRIM(:createdBy), ''), 'SYSTEM'),
                    now(),
                    COALESCE(NULLIF(TRIM(:createdBy), ''), 'SYSTEM')
                )
                ON CONFLICT (project_id, external_ticket_key) DO UPDATE
                SET title = EXCLUDED.title,
                    status = EXCLUDED.status,
                    last_commit_at = COALESCE(EXCLUDED.last_commit_at, tbl_dim_ticket.last_commit_at),
                    updated_at = now(),
                    updated_by = EXCLUDED.updated_by
                RETURNING ticket_id
                """,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("externalTicketKey", canonicalTicketKey(externalTicketKey))
                        .addValue("title", (title == null || title.isBlank()) ? externalTicketKey : title)
                        .addValue("status", status)
                        .addValue("lastCommitAt", lastCommitAt)
                        .addValue("createdBy", createdBy),
                UUID.class);
        return new TicketScope(ticketId, externalTicketKey);
    }

    @Override
    public ConnectorScope ensureConnector(String connectorType, String connectorName) {
        var rows = jdbc.query("""
                SELECT connector_id, connector_type, connector_name
                FROM tbl_source_connector
                WHERE connector_type = :connectorType
                ORDER BY created_at ASC
                LIMIT 1
                """,
                new MapSqlParameterSource("connectorType", connectorType),
                (rs, rowNum) -> new ConnectorScope(
                        rs.getObject("connector_id", UUID.class),
                        rs.getString("connector_type"),
                        rs.getString("connector_name")));
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        UUID connectorId = jdbc.queryForObject("""
                INSERT INTO tbl_source_connector (
                    project_id, repository_id, connector_type, connector_name,
                    config_hash, enabled, created_by, updated_by
                )
                VALUES (
                    NULL, NULL, :connectorType, :connectorName,
                    NULL, TRUE, :actor, :actor
                )
                RETURNING connector_id
                """,
                new MapSqlParameterSource()
                        .addValue("connectorType", connectorType)
                        .addValue("connectorName", connectorName)
                        .addValue("actor", SYSTEM_ACTOR),
                UUID.class);
        return new ConnectorScope(connectorId, connectorType, connectorName);
    }

    @Override
    public CollectorRun insertRun(CollectorRun run) {
        UUID id = jdbc.queryForObject("""
                INSERT INTO tbl_connector_run (
                    connector_id, started_at, finished_at, status,
                    records_read, records_written, failure_count, error_message, trace_id,
                    created_by, updated_by
                )
                VALUES (
                    :connectorId, :startedAt, :finishedAt, CAST(:status AS run_status),
                    :recordsRead, :recordsWritten, :failureCount, :errorMessage, :traceId,
                    :actor, :actor
                )
                RETURNING connector_run_id
                """,
                new MapSqlParameterSource()
                        .addValue("connectorId", run.connectorId())
                        .addValue("startedAt", run.startedAt())
                        .addValue("finishedAt", run.finishedAt())
                        .addValue("status", run.status())
                        .addValue("recordsRead", run.recordsRead())
                        .addValue("recordsWritten", run.recordsWritten())
                        .addValue("failureCount", run.failureCount())
                        .addValue("errorMessage", run.errorMessage())
                        .addValue("traceId", run.traceId())
                        .addValue("actor", SYSTEM_ACTOR),
                UUID.class);
        return new CollectorRun(id, run.connectorId(), run.startedAt(), run.finishedAt(), run.status(), run.recordsRead(), run.recordsWritten(), run.failureCount(), run.errorMessage(), run.traceId());
    }

    @Override
    public CollectorRun updateRun(CollectorRun run) {
        jdbc.update("""
                UPDATE tbl_connector_run
                SET finished_at = :finishedAt,
                    status = CAST(:status AS run_status),
                    records_read = :recordsRead,
                    records_written = :recordsWritten,
                    failure_count = :failureCount,
                    error_message = :errorMessage,
                    trace_id = :traceId,
                    updated_by = :actor
                WHERE connector_run_id = :connectorRunId
                """,
                new MapSqlParameterSource()
                        .addValue("connectorRunId", run.connectorRunId())
                        .addValue("finishedAt", run.finishedAt())
                        .addValue("status", run.status())
                        .addValue("recordsRead", run.recordsRead())
                        .addValue("recordsWritten", run.recordsWritten())
                        .addValue("failureCount", run.failureCount())
                        .addValue("errorMessage", run.errorMessage())
                        .addValue("traceId", run.traceId())
                        .addValue("actor", SYSTEM_ACTOR));
        return run;
    }

    @Override
    public Optional<UUID> findMemberKeyByPseudonym(String pseudonym) {
        if (pseudonym == null || pseudonym.isBlank()) {
            return Optional.empty();
        }
        var rows = jdbc.query("""
                SELECT member_key
                FROM tbl_dim_member_pseudonym
                WHERE LOWER(TRIM(pseudonym)) = LOWER(TRIM(:pseudonym))
                """,
                new MapSqlParameterSource("pseudonym", pseudonym),
                (rs, rowNum) -> rs.getObject("member_key", UUID.class));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<UUID> findMemberKeyByExternalUserHash(String externalUserHash) {
        if (externalUserHash == null || externalUserHash.isBlank()) {
            return Optional.empty();
        }
        var rows = jdbc.query("""
                SELECT member_key
                FROM tbl_dim_member_pseudonym
                WHERE external_user_hash = :externalUserHash
                """,
                new MapSqlParameterSource("externalUserHash", externalUserHash),
                (rs, rowNum) -> rs.getObject("member_key", UUID.class));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<String> findFullnameByMemberKey(UUID memberKey) {
        if (memberKey == null) {
            return Optional.empty();
        }
        var rows = jdbc.query("""
                SELECT fullname
                FROM tbl_auth_user_account
                WHERE member_key = :memberKey
                  AND fullname IS NOT NULL
                  AND BTRIM(fullname) <> ''
                """,
                new MapSqlParameterSource("memberKey", memberKey),
                (rs, rowNum) -> rs.getString("fullname"));
        return rows.stream().findFirst();
    }

    @Override
    public UUID upsertPullRequest(PullRequestUpsert request) {
        return jdbc.queryForObject("""
                INSERT INTO tbl_fact_pull_request (
                    repository_id, ticket_id, external_pr_number, external_pr_id,
                    title, description_hash, status, source_branch, target_branch,
                    opened_at, merged_at, closed_at, external_pr_url, external_updated_at,
                    review_state, author_member_key, author_display_name, linked_issue_key, labels, collected_at,
                    created_by, updated_by
                )
                VALUES (
                    :repositoryId, :ticketId, :externalPrNumber, :externalPrId,
                    :title, :descriptionHash, CAST(:status AS pr_status), :sourceBranch, :targetBranch,
                    :openedAt, :mergedAt, :closedAt, :externalPrUrl, :externalUpdatedAt,
                    CAST(:reviewState AS review_state), :authorMemberKey, :authorDisplayName, :linkedIssueKey, CAST(:labelsJson AS jsonb), :collectedAt,
                    :actor, :actor
                )
                ON CONFLICT (repository_id, external_pr_id) DO UPDATE
                SET ticket_id = EXCLUDED.ticket_id,
                    external_pr_number = EXCLUDED.external_pr_number,
                    title = EXCLUDED.title,
                    description_hash = EXCLUDED.description_hash,
                    status = EXCLUDED.status,
                    source_branch = EXCLUDED.source_branch,
                    target_branch = EXCLUDED.target_branch,
                    opened_at = EXCLUDED.opened_at,
                    merged_at = EXCLUDED.merged_at,
                    closed_at = EXCLUDED.closed_at,
                    external_pr_url = EXCLUDED.external_pr_url,
                    external_updated_at = EXCLUDED.external_updated_at,
                    review_state = EXCLUDED.review_state,
                    author_member_key = EXCLUDED.author_member_key,
                    author_display_name = EXCLUDED.author_display_name,
                    linked_issue_key = EXCLUDED.linked_issue_key,
                    labels = EXCLUDED.labels,
                    collected_at = EXCLUDED.collected_at,
                    updated_at = now(),
                    updated_by = EXCLUDED.updated_by
                RETURNING pr_id
                """,
                new MapSqlParameterSource()
                        .addValue("repositoryId", request.repositoryId())
                        .addValue("ticketId", request.ticketId())
                        .addValue("externalPrNumber", request.externalPrNumber())
                        .addValue("externalPrId", request.externalPrId())
                        .addValue("title", request.title())
                        .addValue("descriptionHash", request.descriptionHash())
                        .addValue("status", request.status())
                        .addValue("sourceBranch", request.sourceBranch())
                        .addValue("targetBranch", request.targetBranch())
                        .addValue("openedAt", request.openedAt())
                        .addValue("mergedAt", request.mergedAt())
                        .addValue("closedAt", request.closedAt())
                        .addValue("externalPrUrl", request.externalPrUrl())
                        .addValue("externalUpdatedAt", request.updatedAt())
                        .addValue("reviewState", request.reviewState())
                        .addValue("authorMemberKey", request.authorMemberKey())
                        .addValue("authorDisplayName", request.authorDisplayName())
                        .addValue("linkedIssueKey", request.linkedIssueKey())
                        .addValue("labelsJson", request.labelsJson())
                        .addValue("collectedAt", request.collectedAt())
                        .addValue("actor", SYSTEM_ACTOR),
                UUID.class);
    }

    @Override
    public UUID upsertCommit(CommitUpsert request) {
        return jdbc.queryForObject("""
                INSERT INTO tbl_fact_commit (
                    repository_id, ticket_id, commit_hash, author_pseudonym,
                    committed_at, branch_name, message_hash, changed_file_count,
                    added_lines, deleted_lines, commit_url, collected_at,
                    created_by, updated_by
                )
                VALUES (
                    :repositoryId, :ticketId, :commitHash, :authorPseudonym,
                    :committedAt, :branchName, :messageHash, :changedFileCount,
                    :addedLines, :deletedLines, :commitUrl, :collectedAt,
                    :actor, :actor
                )
                ON CONFLICT (repository_id, commit_hash) DO UPDATE
                SET ticket_id = EXCLUDED.ticket_id,
                    author_pseudonym = EXCLUDED.author_pseudonym,
                    committed_at = EXCLUDED.committed_at,
                    branch_name = EXCLUDED.branch_name,
                    message_hash = EXCLUDED.message_hash,
                    changed_file_count = EXCLUDED.changed_file_count,
                    added_lines = EXCLUDED.added_lines,
                    deleted_lines = EXCLUDED.deleted_lines,
                    commit_url = EXCLUDED.commit_url,
                    collected_at = EXCLUDED.collected_at,
                    updated_at = now(),
                    updated_by = EXCLUDED.updated_by
                RETURNING commit_id
                """,
                new MapSqlParameterSource()
                        .addValue("repositoryId", request.repositoryId())
                        .addValue("ticketId", request.ticketId())
                        .addValue("commitHash", request.commitHash())
                        .addValue("authorPseudonym", request.authorPseudonym())
                        .addValue("committedAt", request.committedAt())
                        .addValue("branchName", request.branchName())
                        .addValue("messageHash", request.messageHash())
                        .addValue("changedFileCount", request.changedFileCount())
                        .addValue("addedLines", request.addedLines())
                        .addValue("deletedLines", request.deletedLines())
                        .addValue("commitUrl", request.commitUrl())
                        .addValue("collectedAt", request.collectedAt())
                        .addValue("actor", SYSTEM_ACTOR),
                UUID.class);
    }

    @Override
    public void upsertPullRequestCommit(UUID prId, UUID commitId) {
        jdbc.update("""
                INSERT INTO tbl_fact_pull_request_commit (
                    pr_id, commit_id, created_by, updated_by
                )
                VALUES (
                    :prId, :commitId, :actor, :actor
                )
                ON CONFLICT (pr_id, commit_id) DO UPDATE
                SET updated_at = now(),
                    updated_by = EXCLUDED.updated_by
                """,
                new MapSqlParameterSource()
                        .addValue("prId", prId)
                        .addValue("commitId", commitId)
                        .addValue("actor", SYSTEM_ACTOR));
    }

    @Override
    public UUID upsertPullRequestChangedFile(PullRequestChangedFileUpsert request) {
        return jdbc.queryForObject("""
                INSERT INTO tbl_fact_pull_request_changed_file (
                    pr_id, repository_id, file_path, file_path_hash, file_extension,
                    change_type, additions, deletions, collected_at, created_by, updated_by
                )
                VALUES (
                    :prId, :repositoryId, :filePath, :filePathHash, :fileExtension,
                    :changeType, :additions, :deletions, :collectedAt, :actor, :actor
                )
                ON CONFLICT (pr_id, file_path_hash) DO UPDATE
                SET repository_id = EXCLUDED.repository_id,
                    file_path = EXCLUDED.file_path,
                    file_extension = EXCLUDED.file_extension,
                    change_type = EXCLUDED.change_type,
                    additions = EXCLUDED.additions,
                    deletions = EXCLUDED.deletions,
                    collected_at = EXCLUDED.collected_at,
                    updated_at = now(),
                    updated_by = EXCLUDED.updated_by
                RETURNING pull_request_changed_file_id
                """,
                new MapSqlParameterSource()
                        .addValue("prId", request.prId())
                        .addValue("repositoryId", request.repositoryId())
                        .addValue("filePath", request.filePath())
                        .addValue("filePathHash", request.filePathHash())
                        .addValue("fileExtension", request.fileExtension())
                        .addValue("changeType", request.changeType())
                        .addValue("additions", request.additions())
                        .addValue("deletions", request.deletions())
                        .addValue("collectedAt", request.collectedAt())
                        .addValue("actor", SYSTEM_ACTOR),
                UUID.class);
    }

    @Override
    public void upsertTraceabilityLink(TraceabilityLinkUpsert request) {
        jdbc.update("""
                INSERT INTO tbl_fact_traceability_link (
                    ticket_id, source_type, source_id, target_type, target_id,
                    evidence, rule_name, created_by_source, created_by, updated_by
                )
                VALUES (
                    :ticketId, :sourceType, :sourceId, :targetType, :targetId,
                    CAST(:evidenceJson AS jsonb), :ruleName, :actor, :actor, :actor
                )
                ON CONFLICT (source_type, source_id, target_type, target_id) DO UPDATE
                SET ticket_id = EXCLUDED.ticket_id,
                    evidence = EXCLUDED.evidence,
                    rule_name = EXCLUDED.rule_name,
                    updated_at = now(),
                    updated_by = EXCLUDED.updated_by
                """,
                new MapSqlParameterSource()
                        .addValue("ticketId", request.ticketId())
                        .addValue("sourceType", request.sourceType())
                        .addValue("sourceId", request.sourceId())
                        .addValue("targetType", request.targetType())
                        .addValue("targetId", request.targetId())
                .addValue("ruleName", request.ruleName())
                .addValue("evidenceJson", request.evidenceJson())
                .addValue("actor", SYSTEM_ACTOR));
    }

    @Override
    public Optional<UUID> findTicketIdByPullRequestId(UUID prId) {
        return jdbc.query("""
                        SELECT ticket_id
                        FROM tbl_fact_pull_request
                        WHERE pr_id = :prId
                        """,
                new MapSqlParameterSource("prId", prId),
                (rs, rowNum) -> rs.getObject("ticket_id", UUID.class))
                .stream()
                .findFirst();
    }

    @Override
    public void deleteReviewsByPrId(UUID prId) {
        jdbc.update(
                "DELETE FROM tbl_fact_review WHERE pr_id = :prId",
                new MapSqlParameterSource().addValue("prId", prId));
    }

    @Override
    public UUID insertReview(ReviewUpsert request) {
        return jdbc.queryForObject("""
                INSERT INTO tbl_fact_review (
                    pr_id, ticket_id, reviewer_member_key, state, submitted_at, submitted_by, comment_count,
                    collected_at, created_by, updated_by
                )
                VALUES (
                    :prId, :ticketId, :reviewerMemberKey, CAST(:state AS review_state), :submittedAt, :submittedBy, :commentCount,
                    :collectedAt, :actor, :actor
                )
                RETURNING review_id
                """,
                new MapSqlParameterSource()
                        .addValue("prId", request.prId())
                        .addValue("ticketId", request.ticketId())
                        .addValue("reviewerMemberKey", request.reviewerMemberKey())
                        .addValue("state", request.state())
                        .addValue("submittedAt", request.submittedAt())
                        .addValue("submittedBy", request.submittedBy())
                        .addValue("commentCount", request.commentCount())
                        .addValue("collectedAt", request.collectedAt())
                        .addValue("actor", SYSTEM_ACTOR),
                UUID.class);
    }

    @Override
    public void insertReviewComment(ReviewCommentUpsert request) {
        jdbc.update("""
                INSERT INTO tbl_fact_review_comment (
                    review_id, pr_id, ticket_id, comment_summary,
                    file_path_hash, line_number, created_by, updated_by
                )
                VALUES (
                    :reviewId, :prId, :ticketId, :commentHash,
                    :filePathHash, :lineNumber, :actor, :actor
                )
                """,
                new MapSqlParameterSource()
                        .addValue("reviewId", request.reviewId())
                        .addValue("prId", request.prId())
                        .addValue("ticketId", request.ticketId())
                        .addValue("commentHash", request.commentHash())
                        .addValue("filePathHash", request.filePathHash())
                        .addValue("lineNumber", request.lineNumber())
                        .addValue("actor", SYSTEM_ACTOR));
    }
}
