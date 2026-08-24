package com.sdd.platform.infrastructure.persistence.adapter.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.persistence.ArtifactScannerPersistencePort;
import com.sdd.platform.application.usecase.scanner.ArtifactDocumentDateService;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactSnapshot;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedSummaryPatch;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactTypeScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ConnectorScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.DataQualityRecord;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.EvidenceEvent;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedAcceptanceCriteria;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedDecision;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedIssue;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedRisk;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedSection;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.RepositoryScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ScanRun;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedException;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.TicketArtifactEvidence;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.TicketScope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ArtifactScannerJdbcAdapter implements ArtifactScannerPersistencePort {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ArtifactScannerJdbcAdapter(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<RepositoryScope> findRepository(UUID repositoryId) {
        var rows = jdbc.query("""
                SELECT repository_id, project_id, repo_name_masked, default_branch
                FROM tbl_dim_repository
                WHERE repository_id = :repositoryId
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
    public Optional<RepositoryScope> findRepositoryByMaskedName(String repoNameMasked) {
        var rows = jdbc.query("""
                SELECT repository_id, project_id, repo_name_masked, default_branch
                FROM tbl_dim_repository
                WHERE repo_name_masked = :repoNameMasked
                """,
                new MapSqlParameterSource("repoNameMasked", repoNameMasked),
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

    @Override
    public TicketScope upsertMinimalTicket(UUID projectId, String externalTicketKey, String title, String status,
            OffsetDateTime lastCommitAt) {
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
                    'SYSTEM',
                    now(),
                    'SYSTEM'
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
                        .addValue("title",
                                (title == null || title.isBlank()) ? canonicalTicketKey(externalTicketKey) : title)
                        .addValue("status", status)
                        .addValue("lastCommitAt", lastCommitAt),
                UUID.class);
        return new TicketScope(ticketId, externalTicketKey);
    }

    @Override
    public List<ArtifactTypeScope> findArtifactTypes() {
        return jdbc.query("""
                SELECT t.artifact_type_id,
                       t.phase_id,
                       p.phase_code,
                       t.artifact_type_code,
                       t.artifact_name,
                       t.default_file_name,
                       t.required_flag
                FROM tbl_dim_artifact_type t
                LEFT JOIN tbl_dim_phase p ON p.phase_id = t.phase_id
                """,
                (rs, rowNum) -> new ArtifactTypeScope(
                        rs.getObject("artifact_type_id", UUID.class),
                        rs.getObject("phase_id", UUID.class),
                        rs.getString("phase_code"),
                        rs.getString("artifact_type_code"),
                        rs.getString("artifact_name"),
                        rs.getString("default_file_name"),
                        rs.getBoolean("required_flag")));
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
    public Optional<ArtifactSnapshot> findLatestSnapshot(UUID repositoryId, String sourcePath, UUID artifactTypeId) {
        var rows = jdbc.query(
                """
                        SELECT artifact_snapshot_id, connector_run_id, repository_id, ticket_id,
                               external_ticket_key, pr_status, last_commit_at, artifact_type_id, phase_id, artifact_type_code, artifact_name,
                               default_file_name, required_flag, phase_code, source_path, exists_flag,
                               content_hash, schema_version, size_bytes, source_updated_at, template_empty_flag,
                               need_parse, scan_status, scan_message, collected_at
                        FROM vw_artifact_inventory_current
                        WHERE repository_id = :repositoryId
                          AND source_path = :sourcePath
                          AND artifact_type_id = :artifactTypeId
                        ORDER BY collected_at DESC, artifact_snapshot_id DESC
                        LIMIT 1
                        """,
                new MapSqlParameterSource()
                        .addValue("repositoryId", repositoryId)
                        .addValue("sourcePath", sourcePath)
                        .addValue("artifactTypeId", artifactTypeId),
                this::mapSnapshot);
        return rows.stream().findFirst();
    }

    @Override
    public ScanRun insertRun(ScanRun run) {
        UUID id = jdbc.queryForObject("""
                INSERT INTO tbl_connector_run (
                    connector_id, started_at, finished_at, status,
                    records_read, records_written, error_message, trace_id,
                    created_by, updated_by
                )
                VALUES (
                    :connectorId, :startedAt, :finishedAt, CAST(:status AS run_status),
                    :recordsRead, :recordsWritten, :errorMessage, :traceId,
                    :createdBy, :updatedBy
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
                        .addValue("errorMessage", run.errorMessage())
                        .addValue("traceId", run.traceId())
                        .addValue("createdBy", "SYSTEM")
                        .addValue("updatedBy", "SYSTEM"),
                UUID.class);
        return runWithId(run, id);
    }

    @Override
    public ScanRun updateRun(ScanRun run) {
        jdbc.update("""
                UPDATE tbl_connector_run
                SET finished_at = :finishedAt,
                    status = CAST(:status AS run_status),
                    records_read = :recordsRead,
                    records_written = :recordsWritten,
                    error_message = :errorMessage,
                    trace_id = :traceId,
                    updated_by = :updatedBy
                WHERE connector_run_id = :connectorRunId
                """,
                new MapSqlParameterSource()
                        .addValue("connectorRunId", run.connectorRunId())
                        .addValue("finishedAt", run.finishedAt())
                        .addValue("status", run.status())
                        .addValue("recordsRead", run.recordsRead())
                        .addValue("recordsWritten", run.recordsWritten())
                        .addValue("errorMessage", run.errorMessage())
                        .addValue("traceId", run.traceId())
                        .addValue("updatedBy", "SYSTEM"));
        return run;
    }

    @Override
    public Optional<ScanRun> findRun(UUID connectorRunId) {
        var rows = jdbc.query("""
                SELECT connector_run_id, connector_id, started_at, finished_at, status,
                       records_read, records_written, error_message, trace_id
                FROM tbl_connector_run
                WHERE connector_run_id = :connectorRunId
                """,
                new MapSqlParameterSource("connectorRunId", connectorRunId),
                (rs, rowNum) -> new ScanRun(
                        rs.getObject("connector_run_id", UUID.class),
                        rs.getObject("connector_id", UUID.class),
                        rs.getObject("started_at", OffsetDateTime.class),
                        rs.getObject("finished_at", OffsetDateTime.class),
                        rs.getString("status"),
                        rs.getInt("records_read"),
                        rs.getInt("records_written"),
                        rs.getString("error_message"),
                        rs.getString("trace_id")));
        return rows.stream().findFirst();
    }

    @Override
    public ArtifactSnapshot insertSnapshot(ArtifactSnapshot snapshot) {
        UUID id = jdbc.queryForObject(
                """
                        INSERT INTO tbl_fact_artifact_snapshot (
                            ticket_id, repository_id, artifact_type_id, phase_id, source_path, source_url_hash,
                            exists_flag, content_hash, schema_version, schema_valid, template_empty_flag,
                            required_fields_missing, parsed_summary, privacy_classification,
                            contains_customer_data, contains_personal_data, contains_secret_detected,
                            source_created_at, source_updated_at, collected_at, parser_version,
                            connector_run_id, size_bytes, scan_status, scan_message, need_parse,
                            created_by, updated_by
                        )
                        VALUES (
                            :ticketId, :repositoryId, :artifactTypeId, :phaseId, :sourcePath, :sourceUrlHash,
                            :existsFlag, :contentHash, :schemaVersion, :schemaValid, :templateEmptyFlag,
                            CAST(:requiredFieldsMissing AS jsonb), CAST(:parsedSummary AS jsonb), :privacyClassification,
                            :containsCustomerData, :containsPersonalData, :containsSecretDetected,
                            :sourceCreatedAt, :sourceUpdatedAt, :collectedAt, :parserVersion,
                            :connectorRunId, :sizeBytes, :scanStatus, :scanMessage, :needParse,
                            :createdBy, :updatedBy
                        )
                        ON CONFLICT ON CONSTRAINT uq_artifact_snapshot DO UPDATE
                        SET ticket_id = EXCLUDED.ticket_id,
                            repository_id = EXCLUDED.repository_id,
                            artifact_type_id = EXCLUDED.artifact_type_id,
                            phase_id = EXCLUDED.phase_id,
                            source_url_hash = EXCLUDED.source_url_hash,
                            exists_flag = EXCLUDED.exists_flag,
                            schema_version = EXCLUDED.schema_version,
                            schema_valid = EXCLUDED.schema_valid,
                            template_empty_flag = EXCLUDED.template_empty_flag,
                            required_fields_missing = COALESCE(tbl_fact_artifact_snapshot.required_fields_missing, EXCLUDED.required_fields_missing),
                            parsed_summary = COALESCE(tbl_fact_artifact_snapshot.parsed_summary, EXCLUDED.parsed_summary),
                            privacy_classification = EXCLUDED.privacy_classification,
                            contains_customer_data = EXCLUDED.contains_customer_data,
                            contains_personal_data = EXCLUDED.contains_personal_data,
                            contains_secret_detected = EXCLUDED.contains_secret_detected,
                            source_created_at = EXCLUDED.source_created_at,
                            source_updated_at = EXCLUDED.source_updated_at,
                            collected_at = EXCLUDED.collected_at,
                            parser_version = EXCLUDED.parser_version,
                            connector_run_id = EXCLUDED.connector_run_id,
                            size_bytes = EXCLUDED.size_bytes,
                            scan_status = EXCLUDED.scan_status,
                            scan_message = EXCLUDED.scan_message,
                            need_parse = EXCLUDED.need_parse,
                            updated_at = now(),
                            updated_by = EXCLUDED.updated_by
                        RETURNING artifact_snapshot_id
                        """,
                new MapSqlParameterSource()
                        .addValue("ticketId", snapshot.ticketId())
                        .addValue("repositoryId", snapshot.repositoryId())
                        .addValue("artifactTypeId", snapshot.artifactTypeId())
                        .addValue("phaseId", snapshot.phaseId())
                        .addValue("sourcePath", snapshot.sourcePath())
                        .addValue("sourceUrlHash", null)
                        .addValue("existsFlag", snapshot.existsFlag())
                        .addValue("contentHash", snapshot.contentHash())
                        .addValue("schemaVersion", snapshot.schemaVersion())
                        .addValue("schemaValid", null)
                        .addValue("templateEmptyFlag", snapshot.templateEmptyFlag())
                        .addValue("requiredFieldsMissing", "[]")
                        .addValue("parsedSummary", "{}")
                        .addValue("privacyClassification", null)
                        .addValue("containsCustomerData", false)
                        .addValue("containsPersonalData", false)
                        .addValue("containsSecretDetected", false)
                        .addValue("sourceCreatedAt", null)
                        .addValue("sourceUpdatedAt", snapshot.sourceUpdatedAt())
                        .addValue("collectedAt", snapshot.collectedAt())
                        .addValue("parserVersion", null)
                        .addValue("connectorRunId", snapshot.connectorRunId())
                        .addValue("sizeBytes", snapshot.sizeBytes())
                        .addValue("scanStatus", snapshot.scanStatus())
                        .addValue("scanMessage", snapshot.scanMessage())
                        .addValue("needParse", snapshot.needParse())
                        .addValue("createdBy", "SYSTEM")
                        .addValue("updatedBy", "SYSTEM"),
                UUID.class);
        return snapshotWithId(snapshot, id);
    }

    @Override
    public ArtifactSnapshot updateSnapshot(ArtifactSnapshot snapshot, UUID artifactSnapshotId) {
        jdbc.update("""
                UPDATE tbl_fact_artifact_snapshot
                SET ticket_id = :ticketId,
                    repository_id = :repositoryId,
                    artifact_type_id = :artifactTypeId,
                    phase_id = :phaseId,
                    source_url_hash = :sourceUrlHash,
                    exists_flag = :existsFlag,
                    content_hash = :contentHash,
                    schema_version = :schemaVersion,
                    schema_valid = :schemaValid,
                    template_empty_flag = :templateEmptyFlag,
                    privacy_classification = :privacyClassification,
                    contains_customer_data = :containsCustomerData,
                    contains_personal_data = :containsPersonalData,
                    contains_secret_detected = :containsSecretDetected,
                    source_created_at = :sourceCreatedAt,
                    source_updated_at = :sourceUpdatedAt,
                    collected_at = :collectedAt,
                    parser_version = :parserVersion,
                    connector_run_id = :connectorRunId,
                    size_bytes = :sizeBytes,
                    scan_status = :scanStatus,
                    scan_message = :scanMessage,
                    need_parse = :needParse,
                    updated_at = now(),
                    updated_by = :updatedBy
                WHERE artifact_snapshot_id = :artifactSnapshotId
                """,
                new MapSqlParameterSource()
                        .addValue("artifactSnapshotId", artifactSnapshotId)
                        .addValue("ticketId", snapshot.ticketId())
                        .addValue("repositoryId", snapshot.repositoryId())
                        .addValue("artifactTypeId", snapshot.artifactTypeId())
                        .addValue("phaseId", snapshot.phaseId())
                        .addValue("sourceUrlHash", null)
                        .addValue("existsFlag", snapshot.existsFlag())
                        .addValue("contentHash", snapshot.contentHash())
                        .addValue("schemaVersion", snapshot.schemaVersion())
                        .addValue("schemaValid", null)
                        .addValue("templateEmptyFlag", snapshot.templateEmptyFlag())
                        .addValue("privacyClassification", null)
                        .addValue("containsCustomerData", false)
                        .addValue("containsPersonalData", false)
                        .addValue("containsSecretDetected", false)
                        .addValue("sourceCreatedAt", null)
                        .addValue("sourceUpdatedAt", snapshot.sourceUpdatedAt())
                        .addValue("collectedAt", snapshot.collectedAt())
                        .addValue("parserVersion", null)
                        .addValue("connectorRunId", snapshot.connectorRunId())
                        .addValue("sizeBytes", snapshot.sizeBytes())
                        .addValue("scanStatus", snapshot.scanStatus())
                        .addValue("scanMessage", snapshot.scanMessage())
                        .addValue("needParse", snapshot.needParse())
                        .addValue("updatedBy", "SYSTEM"));
        return snapshotWithId(snapshot, artifactSnapshotId);
    }

    @Override
    public List<ArtifactSnapshot> findRunArtifacts(UUID connectorRunId) {
        return jdbc.query(
                """
                        SELECT artifact_snapshot_id, connector_run_id, repository_id, ticket_id,
                               external_ticket_key, pr_status, last_commit_at, artifact_type_id, phase_id, artifact_type_code, artifact_name,
                               default_file_name, required_flag, phase_code, source_path, exists_flag,
                               content_hash, schema_version, size_bytes, source_updated_at, template_empty_flag,
                               need_parse, scan_status, scan_message, collected_at
                        FROM vw_artifact_inventory_current
                        WHERE connector_run_id = :connectorRunId
                        ORDER BY collected_at ASC, artifact_snapshot_id ASC
                        """,
                new MapSqlParameterSource("connectorRunId", connectorRunId),
                this::mapSnapshot);
    }

    @Override
    public List<ArtifactSnapshot> findCurrentInventory(UUID repositoryId) {
        return jdbc.query(
                """
                        SELECT artifact_snapshot_id, connector_run_id, repository_id, ticket_id,
                               external_ticket_key, pr_status, last_commit_at, artifact_type_id, phase_id, artifact_type_code, artifact_name,
                               default_file_name, required_flag, phase_code, source_path, exists_flag,
                               content_hash, size_bytes, source_updated_at, template_empty_flag,
                               need_parse, scan_status, scan_message, collected_at
                        FROM vw_artifact_inventory_current
                        WHERE repository_id = :repositoryId
                        ORDER BY source_path ASC, artifact_type_code ASC, collected_at DESC
                        """,
                new MapSqlParameterSource("repositoryId", repositoryId),
                this::mapSnapshot);
    }

    @Override
    public List<TicketArtifactEvidence> findCurrentArtifactEvidence(UUID ticketId) {
        return jdbc.query(
                """
                        SELECT DISTINCT ON (artifact_type.default_file_name)
                               snapshot.artifact_type_id,
                               artifact_type.artifact_type_code,
                               artifact_type.default_file_name,
                               snapshot.exists_flag,
                               snapshot.schema_valid,
                               snapshot.scan_status,
                               snapshot.collected_at
                        FROM tbl_fact_artifact_snapshot snapshot
                        JOIN tbl_dim_artifact_type artifact_type
                          ON artifact_type.artifact_type_id = snapshot.artifact_type_id
                        WHERE snapshot.ticket_id = :ticketId
                          AND artifact_type.default_file_name IN (
                              'spec-pack.md',
                              'impl-plan.md',
                              'self-review.md',
                               'test-plan.md',
                               'test-results.md',
                               'report.md'
                           )
                        ORDER BY artifact_type.default_file_name ASC, snapshot.collected_at DESC, snapshot.artifact_snapshot_id DESC
                        """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> new TicketArtifactEvidence(
                        rs.getObject("artifact_type_id", UUID.class),
                        rs.getString("artifact_type_code"),
                        rs.getString("default_file_name"),
                        rs.getBoolean("exists_flag"),
                        rs.getObject("schema_valid", Boolean.class),
                        rs.getString("scan_status"),
                        rs.getObject("collected_at", OffsetDateTime.class)));
    }

    @Override
    public Optional<UUID> findLatestTicketPhaseId(UUID ticketId) {
        var rows = jdbc.query("""
                SELECT phase_id
                FROM tbl_fact_ticket_phase_status
                WHERE ticket_id = :ticketId
                LIMIT 1
                """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> rs.getObject("phase_id", UUID.class));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<UUID> findPhaseIdByCode(String phaseCode) {
        var rows = jdbc.query("""
                SELECT phase_id
                FROM tbl_dim_phase
                WHERE phase_code = :phaseCode
                """,
                new MapSqlParameterSource("phaseCode", phaseCode),
                (rs, rowNum) -> rs.getObject("phase_id", UUID.class));
        return rows.stream().findFirst();
    }

    @Override
    public void upsertTicketPhaseStatus(UUID ticketId,
            UUID phaseId,
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            boolean blockedFlag,
            String blockReason) {
        jdbc.update("""
                INSERT INTO tbl_fact_ticket_phase_status (
                    ticket_id,
                    phase_id,
                    status,
                    started_at,
                    completed_at,
                    dwell_time_minutes,
                    blocked_flag,
                    block_reason,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by
                )
                VALUES (
                    :ticketId,
                    :phaseId,
                    :status,
                    :startedAt,
                    :completedAt,
                    NULL,
                    :blockedFlag,
                    :blockReason,
                    now(),
                    'SYSTEM',
                    now(),
                    'SYSTEM'
                )
                ON CONFLICT (ticket_id) DO UPDATE
                SET phase_id = EXCLUDED.phase_id,
                    status = EXCLUDED.status,
                    started_at = EXCLUDED.started_at,
                    completed_at = EXCLUDED.completed_at,
                    dwell_time_minutes = EXCLUDED.dwell_time_minutes,
                    blocked_flag = EXCLUDED.blocked_flag,
                    block_reason = EXCLUDED.block_reason,
                    updated_at = now(),
                    updated_by = EXCLUDED.updated_by
                """,
                new MapSqlParameterSource()
                        .addValue("ticketId", ticketId)
                        .addValue("phaseId", phaseId)
                        .addValue("status", status)
                        .addValue("startedAt", startedAt)
                        .addValue("completedAt", completedAt)
                        .addValue("blockedFlag", blockedFlag)
                        .addValue("blockReason", blockReason));
    }

    @Override
    public void deactivateAcceptanceCriteriaByTicketId(UUID ticketId) {
        jdbc.update("""
                UPDATE tbl_fact_acceptance_criteria
                SET status = 'INACTIVE',
                    updated_at = now(),
                    updated_by = 'SYSTEM'
                WHERE ticket_id = :ticketId
                  AND status = 'ACTIVE'
                """,
                new MapSqlParameterSource("ticketId", ticketId));
    }

    @Override
    public void updateTicketStartedAt(UUID ticketId, OffsetDateTime startedAt) {
        jdbc.update("""
                UPDATE tbl_dim_ticket
                SET started_at = :startedAt
                WHERE ticket_id = :ticketId
                """,
                new MapSqlParameterSource()
                        .addValue("ticketId", ticketId)
                        .addValue("startedAt", startedAt));
    }

    @Override
    public void updateTicketCompletedAt(UUID ticketId, OffsetDateTime completedAt) {
        jdbc.update("""
                UPDATE tbl_dim_ticket
                SET completed_at = :completedAt
                WHERE ticket_id = :ticketId
                """,
                new MapSqlParameterSource()
                        .addValue("ticketId", ticketId)
                        .addValue("completedAt", completedAt));
    }

    private ScanRun runWithId(ScanRun run, UUID id) {
        return new ScanRun(
                id,
                run.connectorId(),
                run.startedAt(),
                run.finishedAt(),
                run.status(),
                run.recordsRead(),
                run.recordsWritten(),
                run.errorMessage(),
                run.traceId());
    }

    @Override
    public void updateSnapshotParsedSummary(ParsedSummaryPatch patch) {
        jdbc.update("""
                UPDATE tbl_fact_artifact_snapshot
                SET parsed_summary          = CAST(:parsedSummary AS jsonb),
                    required_fields_missing = CAST(:requiredFieldsMissing AS jsonb),
                    parser_version          = :parserVersion,
                    schema_valid            = :schemaValid,
                    need_parse              = false,
                    updated_at              = now(),
                    updated_by              = 'SYSTEM'
                WHERE artifact_snapshot_id = :artifactSnapshotId
                """,
                new MapSqlParameterSource()
                        .addValue("parsedSummary", toJson(patch.parsedSummary()))
                        .addValue("requiredFieldsMissing", toJson(patch.requiredFieldsMissing()))
                        .addValue("parserVersion", patch.parserVersion())
                        .addValue("schemaValid", patch.schemaValid())
                        .addValue("artifactSnapshotId", patch.artifactSnapshotId()));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private ArtifactSnapshot snapshotWithId(ArtifactSnapshot snapshot, UUID id) {
        return new ArtifactSnapshot(
                id,
                snapshot.connectorRunId(),
                snapshot.repositoryId(),
                snapshot.ticketId(),
                snapshot.ticketExternalKey(),
                snapshot.ticketStatus(),
                snapshot.ticketLastCommitAt(),
                snapshot.artifactTypeId(),
                snapshot.phaseId(),
                snapshot.artifactTypeCode(),
                snapshot.artifactName(),
                snapshot.defaultFileName(),
                snapshot.requiredFlag(),
                snapshot.phaseCode(),
                snapshot.sourcePath(),
                snapshot.existsFlag(),
                snapshot.contentHash(),
                snapshot.schemaVersion(),
                snapshot.sizeBytes(),
                snapshot.sourceUpdatedAt(),
                snapshot.templateEmptyFlag(),
                snapshot.needParse(),
                snapshot.scanStatus(),
                snapshot.scanMessage(),
                snapshot.collectedAt());
    }

    private ArtifactSnapshot mapSnapshot(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ArtifactSnapshot(
                rs.getObject("artifact_snapshot_id", UUID.class),
                rs.getObject("connector_run_id", UUID.class),
                rs.getObject("repository_id", UUID.class),
                rs.getObject("ticket_id", UUID.class),
                rs.getString("external_ticket_key"),
                rs.getString("pr_status"),
                rs.getObject("last_commit_at", OffsetDateTime.class),
                rs.getObject("artifact_type_id", UUID.class),
                rs.getObject("phase_id", UUID.class),
                rs.getString("artifact_type_code"),
                rs.getString("artifact_name"),
                rs.getString("default_file_name"),
                rs.getBoolean("required_flag"),
                rs.getString("phase_code"),
                rs.getString("source_path"),
                rs.getBoolean("exists_flag"),
                rs.getString("content_hash"),
                rs.getObject("schema_version", Integer.class),
                rs.getObject("size_bytes", Long.class),
                rs.getObject("source_updated_at", OffsetDateTime.class),
                rs.getBoolean("template_empty_flag"),
                rs.getBoolean("need_parse"),
                rs.getString("scan_status"),
                rs.getString("scan_message"),
                rs.getObject("collected_at", OffsetDateTime.class));
    }

    @Override
    public void insertParsedAcceptanceCriteria(ParsedAcceptanceCriteria ac) {
        jdbc.update("""
                INSERT INTO tbl_fact_acceptance_criteria (
                    ticket_id, artifact_snapshot_id, ac_key, ac_text_hash, ac_summary, ambiguous_flag,
                    status, created_by, updated_by
                ) VALUES (
                    :ticketId, :artifactSnapshotId, :acKey, :acTextHash, :acSummary, :ambiguousFlag,
                    'ACTIVE', 'SYSTEM', 'SYSTEM'
                ) ON CONFLICT (ticket_id, ac_key) DO UPDATE
                SET artifact_snapshot_id = EXCLUDED.artifact_snapshot_id,
                    ac_text_hash = EXCLUDED.ac_text_hash,
                    ac_summary = EXCLUDED.ac_summary,
                    ambiguous_flag = EXCLUDED.ambiguous_flag,
                    status = EXCLUDED.status,
                    updated_at = now(),
                    updated_by = EXCLUDED.updated_by
                """,
                new MapSqlParameterSource()
                        .addValue("ticketId", ac.ticketId())
                        .addValue("artifactSnapshotId", ac.artifactSnapshotId())
                        .addValue("acKey", ac.acKey())
                        .addValue("acTextHash", ac.acTextHash())
                        .addValue("acSummary", ac.acSummary())
                        .addValue("ambiguousFlag", ac.ambiguousFlag()));
    }

    @Override
    public void deleteParsedSectionsByTicketIdAndSectionType(UUID ticketId, String sectionType) {
        jdbc.update("""
                DELETE FROM tbl_fact_artifact_parsed_section
                WHERE ticket_id = :ticketId
                  AND section_type = :sectionType
                """,
                new MapSqlParameterSource()
                        .addValue("ticketId", ticketId)
                        .addValue("sectionType", sectionType));
    }

    @Override
    public void insertParsedSection(ParsedSection section) {
        jdbc.update("""
                INSERT INTO tbl_fact_artifact_parsed_section (
                    artifact_snapshot_id, ticket_id, section_type, section_key, section_text_hash, section_summary,
                    required_flag, present_flag, valid_flag, parse_warning
                ) VALUES (
                    :artifactSnapshotId, :ticketId, :sectionType, :sectionKey, :sectionTextHash, :sectionSummary,
                    :requiredFlag, :presentFlag, :validFlag, :parseWarning
                )
                """,
                new MapSqlParameterSource()
                        .addValue("artifactSnapshotId", section.artifactSnapshotId())
                        .addValue("ticketId", section.ticketId())
                        .addValue("sectionType", section.sectionType())
                        .addValue("sectionKey", section.sectionKey())
                        .addValue("sectionTextHash", section.sectionTextHash())
                        .addValue("sectionSummary", section.sectionSummary())
                        .addValue("requiredFlag", section.requiredFlag())
                        .addValue("presentFlag", section.presentFlag())
                        .addValue("validFlag", section.validFlag())
                        .addValue("parseWarning", section.parseWarning()));
    }

    @Override
    public void insertParsedDecision(ParsedDecision decision) {
        jdbc.update("""
                INSERT INTO tbl_fact_decision (
                    ticket_id, repository_id, artifact_snapshot_id, decision_key, decision_summary,
                    reason_present, impact_summary
                ) VALUES (
                    :ticketId, :repositoryId, :artifactSnapshotId, :decisionKey, :decisionSummary,
                    :reasonPresent, :impactSummary
                )
                """,
                new MapSqlParameterSource()
                        .addValue("ticketId", decision.ticketId())
                        .addValue("repositoryId", decision.repositoryId())
                        .addValue("artifactSnapshotId", decision.artifactSnapshotId())
                        .addValue("decisionKey", decision.decisionKey())
                        .addValue("decisionSummary", decision.decisionSummary())
                        .addValue("reasonPresent", decision.reasonPresent())
                        .addValue("impactSummary", decision.impactSummary()));
    }

    @Override
    public void deleteParsedRisksByTicketId(UUID ticketId) {
        jdbc.update("""
                DELETE FROM tbl_fact_risk
                WHERE ticket_id = :ticketId
                """,
                new MapSqlParameterSource("ticketId", ticketId));
    }

    @Override
    public void insertParsedRisk(ParsedRisk risk) {
        jdbc.update(
                """
                        INSERT INTO tbl_fact_risk (
                            ticket_id, repository_id, artifact_snapshot_id, risk_key, risk_summary, severity,
                            mitigation_present, mitigation_summary, status, resolved_at
                        ) VALUES (
                            :ticketId, :repositoryId, :artifactSnapshotId, :riskKey, :riskSummary, CAST(:severity AS severity_level),
                            :mitigationPresent, :mitigationSummary, :status,
                            CASE WHEN UPPER(:status) = 'CLOSED' THEN now() ELSE NULL END
                        )
                        """,
                new MapSqlParameterSource()
                        .addValue("ticketId", risk.ticketId())
                        .addValue("repositoryId", risk.repositoryId())
                        .addValue("artifactSnapshotId", risk.artifactSnapshotId())
                        .addValue("riskKey", risk.riskKey())
                        .addValue("riskSummary", risk.riskSummary())
                        .addValue("severity", risk.severity())
                        .addValue("mitigationPresent", risk.mitigationPresent())
                        .addValue("mitigationSummary", risk.mitigationSummary())
                        .addValue("status", risk.status()));
    }

    @Override
    public void deleteParsedIssuesByTicketIdAndSourceType(UUID ticketId, String sourceType) {
        jdbc.update("""
                DELETE FROM tbl_fact_ticket_issue
                WHERE ticket_id = :ticketId
                  AND source_type = :sourceType
                """,
                new MapSqlParameterSource()
                        .addValue("ticketId", ticketId)
                        .addValue("sourceType", sourceType));
    }

    @Override
    public void insertParsedIssue(ParsedIssue issue) {
        jdbc.update("""
                INSERT INTO tbl_fact_ticket_issue (
                    ticket_id,
                    repository_id,
                    artifact_snapshot_id,
                    source_type,
                    issue_order,
                    issue_key,
                    issue_title,
                    issue_impact,
                    issue_owner,
                    issue_status,
                    issue_summary,
                    source_path,
                    collected_at,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by
                ) VALUES (
                    :ticketId,
                    :repositoryId,
                    :artifactSnapshotId,
                    :sourceType,
                    :issueOrder,
                    :issueKey,
                    :issueTitle,
                    :issueImpact,
                    :issueOwner,
                    :issueStatus,
                    :issueSummary,
                    :sourcePath,
                    :collectedAt,
                    now(),
                    'SYSTEM',
                    now(),
                    'SYSTEM'
                )
                """,
                new MapSqlParameterSource()
                        .addValue("ticketId", issue.ticketId())
                        .addValue("repositoryId", issue.repositoryId())
                        .addValue("artifactSnapshotId", issue.artifactSnapshotId())
                        .addValue("sourceType", issue.sourceType())
                        .addValue("issueOrder", issue.issueOrder())
                        .addValue("issueKey", issue.issueKey())
                        .addValue("issueTitle", issue.issueTitle())
                        .addValue("issueImpact", issue.issueImpact())
                        .addValue("issueOwner", issue.issueOwner())
                        .addValue("issueStatus", issue.issueStatus())
                        .addValue("issueSummary", issue.issueSummary())
                        .addValue("sourcePath", issue.sourcePath())
                        .addValue("collectedAt", issue.collectedAt()));
    }

    @Override
    public void insertEvidenceEvent(
            com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.EvidenceEvent event) {
        jdbc.update("""
                INSERT INTO tbl_fact_evidence_event (
                    ticket_id, repository_id, artifact_snapshot_id, event_type, result,
                    summary, metadata, event_timestamp, source_type, source_ref_id
                ) VALUES (
                    :ticketId, :repositoryId, :artifactSnapshotId, :eventType, :result,
                    :summary, CAST(:metadata AS jsonb), now(), :source_type, :artifactSnapshotId
                )
                """,
                new MapSqlParameterSource()
                        .addValue("ticketId", event.ticketId())
                        .addValue("repositoryId", event.repositoryId())
                        .addValue("source_type", event.sourceType())
                        .addValue("artifactSnapshotId", event.artifactSnapshotId())
                        .addValue("eventType", event.eventType())
                        .addValue("result", event.eventResult())
                        .addValue("summary", event.eventSummary())
                        .addValue("metadata", event.eventMetadata() != null ? event.eventMetadata() : "{}"));
    }

    @Override
    public void insertDataQualityRecord(
            com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.DataQualityRecord quality) {
        jdbc.update("""
                INSERT INTO tbl_fact_data_quality (
                    connector_run_id, project_id, repository_id, source_type, source_ref, missing_count,
                    parse_error_count, schema_violation_count, freshness_delay_minutes, error_summary
                ) VALUES (
                    :connectorRunId, :projectId, :repositoryId, :sourceType, :sourceRef, :missingCount,
                    :parseErrorCount, :schemaViolationCount, :freshnessDelayMinutes, :errorSummary
                )
                    ON CONFLICT ON CONSTRAINT unique_tbl_fact_data_quality DO UPDATE 
                SET
                    connector_run_id = EXCLUDED.connector_run_id
                    , missing_count = EXCLUDED.missing_count
                    , parse_error_count = EXCLUDED.parse_error_count
                    , schema_violation_count = EXCLUDED.schema_violation_count
                    , freshness_delay_minutes = EXCLUDED.freshness_delay_minutes
                    , error_summary = EXCLUDED.error_summary
                    , updated_at = now()
                """,
                new MapSqlParameterSource()
                        .addValue("connectorRunId", quality.connectorRunId())
                        .addValue("projectId", quality.projectId())
                        .addValue("repositoryId", quality.repositoryId())
                        .addValue("sourceType", quality.sourceType())
                        .addValue("sourceRef", quality.sourceRef())
                        .addValue("missingCount", quality.missingCount())
                        .addValue("parseErrorCount", quality.parseErrorCount())
                        .addValue("schemaViolationCount", quality.schemaViolationCount())
                        .addValue("freshnessDelayMinutes", quality.freshnessDelayMinutes())
                        .addValue("errorSummary", quality.errorSummary()));
    }

    @Override
    public Optional<UUID> findRoleIdByName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return Optional.empty();
        }
        var rows = jdbc.query("""
                SELECT role_id
                FROM tbl_dim_role
                WHERE UPPER(TRIM(role_name)) = UPPER(TRIM(:roleName))
                LIMIT 1
                """,
                new MapSqlParameterSource("roleName", roleName.trim()),
                (rs, rowNum) -> rs.getObject("role_id", UUID.class));
        return rows.stream().findFirst();
    }

    @Override
    public void upsertParsedExceptions(List<ParsedException> exceptions) {
        if (exceptions == null || exceptions.isEmpty()) {
            return;
        }
        for (ParsedException ex : exceptions) {
            jdbc.update("""
                    INSERT INTO tbl_fact_exception (
                        exception_id,
                        ticket_id,
                        repository_id,
                        exception_type,
                        reason_present,
                        reason,
                        alternative_check,
                        approved,
                        approved_by_role_id,
                        follow_up_status,
                        status,
                        linked_report_path,
                        source_section,
                        created_at,
                        updated_at,
                        created_by,
                        updated_by
                    )
                    VALUES (
                        gen_random_uuid(),
                        :ticketId,
                        :repositoryId,
                        :exceptionType,
                        :reasonPresent,
                        :reason,
                        :alternativeCheck,
                        :approved,
                        :approvedByRoleId,
                        :followUpStatus,
                        :status,
                        :linkedReportPath,
                        :sourceSection,
                        now(),
                        now(),
                        'SYSTEM',
                        'SYSTEM'
                    )
                    ON CONFLICT (ticket_id, exception_type, source_section)
                        WHERE ticket_id IS NOT NULL
                    DO UPDATE SET
                        reason_present      = EXCLUDED.reason_present,
                        reason              = EXCLUDED.reason,
                        alternative_check   = EXCLUDED.alternative_check,
                        approved            = EXCLUDED.approved,
                        approved_by_role_id = EXCLUDED.approved_by_role_id,
                        follow_up_status    = EXCLUDED.follow_up_status,
                        status              = EXCLUDED.status,
                        linked_report_path  = EXCLUDED.linked_report_path,
                        updated_at          = now()
                    """,
                    new MapSqlParameterSource()
                            .addValue("ticketId", ex.ticketId())
                            .addValue("repositoryId", ex.repositoryId())
                            .addValue("exceptionType", ex.exceptionType())
                            .addValue("reasonPresent", ex.reasonPresent())
                            .addValue("reason", ex.reason())
                            .addValue("alternativeCheck", ex.alternativeCheck())
                            .addValue("approved", ex.approved())
                            .addValue("approvedByRoleId", resolveRoleId(ex.approvedByRoleName()))
                            .addValue("followUpStatus", ex.followUpStatus())
                            .addValue("status", ex.status())
                            .addValue("linkedReportPath", ex.linkedReportPath())
                            .addValue("sourceSection", ex.sourceSection()));
        }
    }

    @Override
    public void deleteParsedExceptionsByTicketId(UUID ticketId) {
        jdbc.update("""
                DELETE FROM tbl_fact_exception
                WHERE ticket_id = :ticketId
                """,
                new MapSqlParameterSource("ticketId", ticketId));
    }

    private UUID resolveRoleId(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return null;
        }
        return findRoleIdByName(roleName).orElse(null);
    }

    private static String canonicalTicketKey(String externalTicketKey) {
        return externalTicketKey == null ? null : externalTicketKey.trim().toUpperCase();
    }

    @Override
    public void upsertArtifactDocumentDates(List<ArtifactDocumentDateService.DocumentDate> documentDates) {
        if (documentDates == null || documentDates.isEmpty()) {
            return;
        }
        for (ArtifactDocumentDateService.DocumentDate documentDate : documentDates) {
            jdbc.update("""
                    INSERT INTO tbl_fact_artifact_document_date (
                        artifact_snapshot_id,
                        document_create_at,
                        document_update_at,
                        created_by,
                        updated_by
                    )
                    VALUES (
                        :artifactSnapshotId,
                        :documentCreateAt,
                        :documentUpdateAt,
                        'SYSTEM',
                        'SYSTEM'
                    )
                    ON CONFLICT (artifact_snapshot_id) DO NOTHING
                    """,
                    new MapSqlParameterSource()
                            .addValue("artifactSnapshotId", documentDate.artifactSnapshotId())
                            .addValue("documentCreateAt", documentDate.createAt())
                            .addValue("documentUpdateAt", documentDate.updateAt()));
        }
    }
}
