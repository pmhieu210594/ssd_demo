package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.TraceabilityRepositoryPort;
import com.sdd.platform.application.usecase.traceability.TraceabilityModels.ArtifactCoverageRow;
import com.sdd.platform.application.usecase.traceability.TraceabilityModels.CiRunCoverageRow;
import com.sdd.platform.application.usecase.traceability.TraceabilityModels.CommitCoverageRow;
import com.sdd.platform.application.usecase.traceability.TraceabilityModels.EvidenceEventRow;
import com.sdd.platform.application.usecase.traceability.TraceabilityModels.ParsedSectionRow;
import com.sdd.platform.application.usecase.traceability.TraceabilityModels.PullRequestCoverageRow;
import com.sdd.platform.application.usecase.traceability.TraceabilityModels.TicketRow;
import com.sdd.platform.application.usecase.traceability.TraceabilityModels.TraceabilityLinkRow;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TraceabilityJdbcAdapter implements TraceabilityRepositoryPort {

    private final NamedParameterJdbcTemplate jdbc;

    public TraceabilityJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<TicketRow> findTicket(UUID ticketId) {
        List<TicketRow> rows = jdbc.query("""
                        SELECT ticket_id, project_id, external_ticket_key, title, status::text AS status
                        FROM tbl_dim_ticket
                        WHERE ticket_id = :ticketId
                        """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> new TicketRow(
                        rs.getObject("ticket_id", UUID.class),
                        rs.getObject("project_id", UUID.class),
                        rs.getString("external_ticket_key"),
                        rs.getString("title"),
                        rs.getString("status")));
        return rows.stream().findFirst();
    }

    @Override
    public List<ArtifactCoverageRow> findArtifacts(UUID ticketId) {
        return jdbc.query("""
                        SELECT
                            a.artifact_snapshot_id,
                            t.artifact_type_code,
                            t.artifact_name,
                            t.default_file_name,
                            t.required_flag,
                            a.source_path,
                            a.exists_flag,
                            COALESCE(a.collected_at, a.created_at) AS collected_at
                        FROM tbl_fact_artifact_snapshot a
                        JOIN tbl_dim_artifact_type t ON t.artifact_type_id = a.artifact_type_id
                        WHERE a.ticket_id = :ticketId
                          AND t.artifact_type_code IN (
                              'SPEC_PACK', 'IMPL_PLAN', 'REVIEW_CHECKLIST', 'SELF_REVIEW',
                              'TEST_PLAN', 'TEST_RESULTS', 'REPORT'
                          )
                        ORDER BY COALESCE(a.collected_at, a.created_at) DESC, a.artifact_snapshot_id DESC
                        """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> new ArtifactCoverageRow(
                        rs.getObject("artifact_snapshot_id", UUID.class),
                        rs.getString("artifact_type_code"),
                        rs.getString("artifact_name"),
                        rs.getString("default_file_name"),
                        rs.getBoolean("required_flag"),
                        rs.getString("source_path"),
                        rs.getBoolean("exists_flag"),
                        rs.getObject("collected_at", java.time.OffsetDateTime.class)));
    }

    @Override
    public List<PullRequestCoverageRow> findPullRequests(UUID ticketId) {
        return jdbc.query("""
                        SELECT
                            pr_id,
                            external_pr_id,
                            external_pr_url,
                            title,
                            status::text AS status,
                            source_branch,
                            target_branch,
                            opened_at,
                            merged_at,
                            closed_at,
                            COALESCE(collected_at, created_at) AS collected_at
                        FROM tbl_fact_pull_request
                        WHERE ticket_id = :ticketId
                        ORDER BY COALESCE(collected_at, created_at) DESC, pr_id DESC
                        """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> new PullRequestCoverageRow(
                        rs.getObject("pr_id", UUID.class),
                        rs.getString("external_pr_id"),
                        rs.getString("external_pr_url"),
                        rs.getString("title"),
                        rs.getString("status"),
                        rs.getString("source_branch"),
                        rs.getString("target_branch"),
                        rs.getObject("opened_at", java.time.OffsetDateTime.class),
                        rs.getObject("merged_at", java.time.OffsetDateTime.class),
                        rs.getObject("closed_at", java.time.OffsetDateTime.class),
                        rs.getObject("collected_at", java.time.OffsetDateTime.class)));
    }

    @Override
    public List<CommitCoverageRow> findCommits(UUID ticketId) {
        return jdbc.query("""
                        SELECT
                            c.commit_id,
                            c.commit_hash,
                            c.branch_name,
                            c.message_hash,
                            c.committed_at,
                            COALESCE(c.collected_at, c.created_at) AS collected_at
                        FROM tbl_fact_pull_request pr
                        JOIN tbl_fact_pull_request_commit prc ON prc.pr_id = pr.pr_id
                        JOIN tbl_fact_commit c ON c.commit_id = prc.commit_id
                        WHERE pr.ticket_id = :ticketId
                        ORDER BY COALESCE(c.committed_at, c.collected_at, c.created_at) ASC, c.commit_id ASC
                        """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> new CommitCoverageRow(
                        rs.getObject("commit_id", UUID.class),
                        rs.getString("commit_hash"),
                        rs.getString("branch_name"),
                        rs.getString("message_hash"),
                        rs.getObject("committed_at", java.time.OffsetDateTime.class),
                        rs.getObject("collected_at", java.time.OffsetDateTime.class)));
    }

    @Override
    public List<CiRunCoverageRow> findCiRuns(UUID ticketId) {
        return jdbc.query("""
                        SELECT
                            ci_run_id,
                            external_ci_run_id,
                            ci_url,
                            workflow_name,
                            status::text AS status,
                            started_at,
                            finished_at,
                            COALESCE(collected_at, created_at) AS collected_at
                        FROM tbl_fact_ci_run
                        WHERE ticket_id = :ticketId
                        ORDER BY COALESCE(collected_at, created_at) DESC, ci_run_id DESC
                        """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> new CiRunCoverageRow(
                        rs.getObject("ci_run_id", UUID.class),
                        rs.getString("external_ci_run_id"),
                        rs.getString("ci_url"),
                        rs.getString("workflow_name"),
                        rs.getString("status"),
                        rs.getObject("started_at", java.time.OffsetDateTime.class),
                        rs.getObject("finished_at", java.time.OffsetDateTime.class),
                        rs.getObject("collected_at", java.time.OffsetDateTime.class)));
    }

    @Override
    public List<TraceabilityLinkRow> findTraceabilityLinks(UUID ticketId) {
        return jdbc.query("""
                        SELECT
                            traceability_link_id,
                            ticket_id,
                            source_type,
                            source_id,
                            target_type,
                            target_id,
                            confidence,
                            confidence_level::text AS confidence_level,
                            rule_name,
                            evidence::text AS evidence_json,
                            created_at
                        FROM tbl_fact_traceability_link
                        WHERE ticket_id = :ticketId
                        ORDER BY created_at ASC, traceability_link_id ASC
                        """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> new TraceabilityLinkRow(
                        rs.getObject("traceability_link_id", UUID.class),
                        rs.getObject("ticket_id", UUID.class),
                        rs.getString("source_type"),
                        rs.getString("source_id"),
                        rs.getString("target_type"),
                        rs.getString("target_id"),
                        rs.getBigDecimal("confidence") == null ? null : rs.getBigDecimal("confidence").doubleValue(),
                        rs.getString("confidence_level"),
                        rs.getString("rule_name"),
                        rs.getString("evidence_json"),
                        rs.getObject("created_at", java.time.OffsetDateTime.class)));
    }

    @Override
    public List<EvidenceEventRow> findEvidenceEvents(UUID ticketId) {
        return jdbc.query("""
                        SELECT
                            evidence_event_id,
                            event_type,
                            source_type,
                            source_ref_id,
                            result,
                            summary,
                            event_timestamp
                        FROM tbl_fact_evidence_event
                        WHERE ticket_id = :ticketId
                        ORDER BY event_timestamp ASC, evidence_event_id ASC
                        """,
                new MapSqlParameterSource("ticketId", ticketId),
                        (rs, rowNum) -> new EvidenceEventRow(
                        rs.getObject("evidence_event_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("source_type"),
                        rs.getString("source_ref_id"),
                        rs.getString("result"),
                        rs.getString("summary"),
                        rs.getObject("event_timestamp", java.time.OffsetDateTime.class)));
    }

    @Override
    public List<ParsedSectionRow> findParsedSections(UUID ticketId) {
        return jdbc.query("""
                        SELECT
                            p.parsed_section_id,
                            p.artifact_snapshot_id,
                            t.artifact_type_code,
                            t.artifact_name,
                            p.section_key,
                            p.section_summary,
                            p.required_flag,
                            p.present_flag,
                            p.valid_flag,
                            p.parse_warning,
                            COALESCE(a.collected_at, a.created_at) AS collected_at
                        FROM tbl_fact_artifact_parsed_section p
                        JOIN tbl_fact_artifact_snapshot a ON a.artifact_snapshot_id = p.artifact_snapshot_id
                        JOIN tbl_dim_artifact_type t ON t.artifact_type_id = a.artifact_type_id
                        WHERE p.ticket_id = :ticketId
                        ORDER BY COALESCE(a.collected_at, a.created_at) DESC, t.artifact_type_code ASC, p.section_key ASC, p.parsed_section_id ASC
                        """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> new ParsedSectionRow(
                        rs.getObject("parsed_section_id", UUID.class),
                        rs.getObject("artifact_snapshot_id", UUID.class),
                        rs.getString("artifact_type_code"),
                        rs.getString("artifact_name"),
                        rs.getString("section_key"),
                        rs.getString("section_summary"),
                        rs.getBoolean("required_flag"),
                        rs.getBoolean("present_flag"),
                        rs.getObject("valid_flag", Boolean.class),
                        rs.getString("parse_warning"),
                        rs.getObject("collected_at", java.time.OffsetDateTime.class)));
    }
}
