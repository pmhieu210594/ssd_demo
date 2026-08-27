package com.sdd.platform.infrastructure.persistence.adapter.docparse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.persistence.DocParsePersistencePort;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseDataQuality;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseEvidenceEvent;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseField;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseMode;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseSnapshot;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class GenericDocParseJdbcAdapter implements DocParsePersistencePort {

    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final String artifactTypeCode;

    public GenericDocParseJdbcAdapter(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper, String artifactTypeCode) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.artifactTypeCode = artifactTypeCode;
    }

    @Override
    public ParseSnapshot upsertSnapshot(ParseSnapshot snapshot) {
        ArtifactTypeLookup lookup = lookupArtifactType();
        String parsedSummaryJson = snapshot.parsedSummaryJson() == null || snapshot.parsedSummaryJson().isBlank()
                ? "{}"
                : snapshot.parsedSummaryJson();
        List<String> missingFields = snapshot.requiredFieldsMissing() == null ? List.of() : snapshot.requiredFieldsMissing();

        UUID id = jdbc.queryForObject("""
                INSERT INTO tbl_fact_artifact_snapshot (
                    ticket_id, repository_id, artifact_type_id, phase_id, source_path, source_url_hash,
                    exists_flag, content_hash, schema_version, schema_valid, template_empty_flag,
                    required_fields_missing, parsed_summary, parser_version,
                    collected_at, created_at, created_by, updated_at, updated_by
                )
                VALUES (
                    :ticketId, :repositoryId, :artifactTypeId, :phaseId, :sourcePath, :sourceUrlHash,
                    :existsFlag, :contentHash, :schemaVersion, :schemaValid, :templateEmptyFlag,
                    CAST(:requiredFieldsMissing AS jsonb), CAST(:parsedSummary AS jsonb), :parserVersion,
                    :collectedAt, now(), :actor, now(), :actor
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
                    required_fields_missing = EXCLUDED.required_fields_missing,
                    parsed_summary = EXCLUDED.parsed_summary,
                    parser_version = EXCLUDED.parser_version,
                    collected_at = EXCLUDED.collected_at,
                    updated_at = now(),
                    updated_by = EXCLUDED.updated_by
                RETURNING artifact_snapshot_id
                """,
                new MapSqlParameterSource()
                        .addValue("ticketId", snapshot.ticketId())
                        .addValue("repositoryId", snapshot.repositoryId())
                        .addValue("artifactTypeId", lookup.artifactTypeId())
                        .addValue("phaseId", lookup.phaseId())
                        .addValue("sourcePath", snapshot.sourcePath())
                        .addValue("sourceUrlHash", null)
                        .addValue("existsFlag", true)
                        .addValue("contentHash", snapshot.contentHash())
                        .addValue("schemaVersion", snapshot.schemaVersion())
                        .addValue("schemaValid", snapshot.schemaValid())
                        .addValue("templateEmptyFlag", snapshot.templateEmptyFlag())
                        .addValue("requiredFieldsMissing", toJson(missingFields))
                        .addValue("parsedSummary", parsedSummaryJson)
                        .addValue("parserVersion", snapshot.parserVersion())
                        .addValue("collectedAt", snapshot.collectedAt())
                        .addValue("actor", SYSTEM_ACTOR),
                UUID.class);
        return new ParseSnapshot(
                id,
                snapshot.ticketId(),
                snapshot.repositoryId(),
                lookup.artifactTypeId(),
                lookup.phaseId(),
                lookup.artifactTypeCode(),
                lookup.artifactName(),
                snapshot.parseMode(),
                snapshot.parseStatus(),
                snapshot.sourcePath(),
                snapshot.contentHash(),
                snapshot.schemaVersion(),
                snapshot.schemaValid(),
                snapshot.templateEmptyFlag(),
                missingFields,
                parsedSummaryJson,
                snapshot.parserVersion(),
                snapshot.collectedAt()
        );
    }

    @Override
    public void replaceSections(UUID snapshotId, UUID ticketId, List<ParseField> sections) {
        jdbc.update("""
                DELETE FROM tbl_fact_artifact_parsed_section
                WHERE artifact_snapshot_id = :snapshotId
                """,
                new MapSqlParameterSource("snapshotId", snapshotId));
        for (ParseField section : sections) {
            jdbc.update("""
                    INSERT INTO tbl_fact_artifact_parsed_section (
                        artifact_snapshot_id, ticket_id, section_type, section_key, section_text_hash,
                        section_summary, required_flag, present_flag, valid_flag, parse_warning,
                        created_at, created_by, updated_at, updated_by
                    )
                    VALUES (
                        :snapshotId, :ticketId, :sectionType, :sectionKey, :sectionTextHash,
                        :sectionSummary, :requiredFlag, :presentFlag, :validFlag, :parseWarning,
                        now(), :actor, now(), :actor
                    )
                    """,
                    new MapSqlParameterSource()
                            .addValue("snapshotId", snapshotId)
                            .addValue("ticketId", ticketId)
                            .addValue("sectionType", section.sectionType())
                            .addValue("sectionKey", section.sectionKey())
                            .addValue("sectionTextHash", section.sectionTextHash())
                            .addValue("sectionSummary", section.sectionSummary())
                            .addValue("requiredFlag", section.requiredFlag())
                            .addValue("presentFlag", section.presentFlag())
                            .addValue("validFlag", section.validFlag())
                            .addValue("parseWarning", section.parseWarning())
                            .addValue("actor", SYSTEM_ACTOR));
        }
    }

    @Override
    public Optional<ParseSnapshot> findLatestSnapshot(UUID ticketId, String typeCode, ParseMode parseMode) {
        return jdbc.query("""
                        SELECT s.artifact_snapshot_id,
                               s.ticket_id,
                               s.repository_id,
                               s.artifact_type_id,
                               s.phase_id,
                               t.artifact_type_code,
                               t.artifact_name,
                               COALESCE(s.parsed_summary ->> 'parseMode', :parseMode) AS parse_mode,
                               COALESCE(s.parsed_summary ->> 'parseStatus', 'PARSE_ERROR') AS parse_status,
                               s.source_path,
                               s.content_hash,
                               s.schema_version,
                               s.schema_valid,
                               s.template_empty_flag,
                               s.required_fields_missing,
                               s.parsed_summary,
                               s.parser_version,
                               s.collected_at
                        FROM tbl_fact_artifact_snapshot s
                        JOIN tbl_dim_artifact_type t ON t.artifact_type_id = s.artifact_type_id
                        WHERE s.ticket_id = :ticketId
                          AND t.artifact_type_code = :artifactTypeCode
                          AND COALESCE(s.parsed_summary ->> 'parseMode', :parseMode) = :parseMode
                        ORDER BY s.collected_at DESC, s.artifact_snapshot_id DESC
                        LIMIT 1
                        """,
                new MapSqlParameterSource()
                        .addValue("ticketId", ticketId)
                        .addValue("artifactTypeCode", typeCode)
                        .addValue("parseMode", parseMode.name()),
                this::mapSnapshot).stream().findFirst();
    }

    @Override
    public Optional<ParseSnapshot> findSnapshotById(UUID snapshotId) {
        return jdbc.query("""
                        SELECT s.artifact_snapshot_id,
                               s.ticket_id,
                               s.repository_id,
                               s.artifact_type_id,
                               s.phase_id,
                               t.artifact_type_code,
                               t.artifact_name,
                               COALESCE(s.parsed_summary ->> 'parseMode', 'DRAFT') AS parse_mode,
                               COALESCE(s.parsed_summary ->> 'parseStatus', 'PARSE_ERROR') AS parse_status,
                               s.source_path,
                               s.content_hash,
                               s.schema_version,
                               s.schema_valid,
                               s.template_empty_flag,
                               s.required_fields_missing,
                               s.parsed_summary,
                               s.parser_version,
                               s.collected_at
                        FROM tbl_fact_artifact_snapshot s
                        JOIN tbl_dim_artifact_type t ON t.artifact_type_id = s.artifact_type_id
                        WHERE s.artifact_snapshot_id = :snapshotId
                        """,
                new MapSqlParameterSource("snapshotId", snapshotId),
                this::mapSnapshot).stream().findFirst();
    }

    @Override
    public List<ParseField> findSections(UUID snapshotId) {
        return jdbc.query("""
                        SELECT parsed_section_id,
                               artifact_snapshot_id,
                               ticket_id,
                               section_type,
                               section_key,
                               section_text_hash,
                               section_summary,
                               required_flag,
                               present_flag,
                               valid_flag,
                               parse_warning
                        FROM tbl_fact_artifact_parsed_section
                        WHERE artifact_snapshot_id = :snapshotId
                        ORDER BY section_key ASC, parsed_section_id ASC
                        """,
                new MapSqlParameterSource("snapshotId", snapshotId),
                (rs, rowNum) -> new ParseField(
                        rs.getObject("parsed_section_id", UUID.class),
                        rs.getObject("artifact_snapshot_id", UUID.class),
                        rs.getObject("ticket_id", UUID.class),
                        rs.getString("section_type"),
                        rs.getString("section_key"),
                        rs.getString("section_text_hash"),
                        rs.getString("section_summary"),
                        rs.getBoolean("required_flag"),
                        rs.getBoolean("present_flag"),
                        (Boolean) rs.getObject("valid_flag"),
                        rs.getString("parse_warning")));
    }

    @Override
    public List<ParseSnapshot> findSnapshots(UUID ticketId, String typeCode, ParseMode parseMode, int limit) {
        int safeLimit = Math.max(Math.min(limit, 100), 1);
        return jdbc.query("""
                        SELECT s.artifact_snapshot_id,
                               s.ticket_id,
                               s.repository_id,
                               s.artifact_type_id,
                               s.phase_id,
                               t.artifact_type_code,
                               t.artifact_name,
                               COALESCE(s.parsed_summary ->> 'parseMode', :parseMode) AS parse_mode,
                               COALESCE(s.parsed_summary ->> 'parseStatus', 'PARSE_ERROR') AS parse_status,
                               s.source_path,
                               s.content_hash,
                               s.schema_version,
                               s.schema_valid,
                               s.template_empty_flag,
                               s.required_fields_missing,
                               s.parsed_summary,
                               s.parser_version,
                               s.collected_at
                        FROM tbl_fact_artifact_snapshot s
                        JOIN tbl_dim_artifact_type t ON t.artifact_type_id = s.artifact_type_id
                        WHERE s.ticket_id = :ticketId
                          AND t.artifact_type_code = :artifactTypeCode
                          AND COALESCE(s.parsed_summary ->> 'parseMode', :parseMode) = :parseMode
                        ORDER BY s.collected_at DESC, s.artifact_snapshot_id DESC
                        LIMIT :limit
                        """,
                new MapSqlParameterSource()
                        .addValue("ticketId", ticketId)
                        .addValue("artifactTypeCode", typeCode)
                        .addValue("parseMode", parseMode.name())
                        .addValue("limit", safeLimit),
                this::mapSnapshot);
    }

    @Override
    public void persistEvidenceEvent(ParseEvidenceEvent event) {
        jdbc.update("""
                INSERT INTO tbl_fact_evidence_event (
                    ticket_id, repository_id, artifact_snapshot_id,
                    event_type, result, summary, metadata, event_timestamp,
                    source_type, source_ref_id,
                    created_at, created_by, updated_at, updated_by
                )
                VALUES (
                    :ticketId, :repositoryId, :artifactSnapshotId,
                    :eventType, :result, :summary, CAST(:metadata AS jsonb), :eventTimestamp,
                    :sourceType, :sourceRefId,
                    now(), :actor, now(), :actor
                )
                """,
                new MapSqlParameterSource()
                        .addValue("ticketId", event.ticketId())
                        .addValue("repositoryId", event.repositoryId())
                        .addValue("artifactSnapshotId", event.artifactSnapshotId())
                        .addValue("eventType", event.eventType())
                        .addValue("result", event.result())
                        .addValue("summary", event.summary())
                        .addValue("metadata", event.metadataJson())
                        .addValue("eventTimestamp", event.eventTimestamp())
                        .addValue("sourceType", event.sourceType())
                        .addValue("sourceRefId", event.sourceRefId())
                        .addValue("actor", SYSTEM_ACTOR));
    }

    @Override
    public void persistDataQuality(ParseDataQuality quality) {
        jdbc.update("""
                INSERT INTO tbl_fact_data_quality (
                    project_id, repository_id, source_type, source_ref,
                    missing_count, parse_error_count, schema_violation_count,
                    error_summary, checked_at,
                    created_at, created_by, updated_at, updated_by
                )
                VALUES (
                    :projectId, :repositoryId, :sourceType, :sourceRef,
                    :missingCount, :parseErrorCount, :schemaViolationCount,
                    :errorSummary, :checkedAt,
                    now(), :actor, now(), :actor
                )
                """,
                new MapSqlParameterSource()
                        .addValue("projectId", quality.projectId())
                        .addValue("repositoryId", quality.repositoryId())
                        .addValue("sourceType", quality.sourceType())
                        .addValue("sourceRef", quality.sourceRef())
                        .addValue("missingCount", quality.missingCount())
                        .addValue("parseErrorCount", quality.parseErrorCount())
                        .addValue("schemaViolationCount", quality.schemaViolationCount())
                        .addValue("errorSummary", quality.errorSummary())
                        .addValue("checkedAt", quality.checkedAt())
                        .addValue("actor", SYSTEM_ACTOR));
    }

    private ParseSnapshot mapSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new ParseSnapshot(
                rs.getObject("artifact_snapshot_id", UUID.class),
                rs.getObject("ticket_id", UUID.class),
                rs.getObject("repository_id", UUID.class),
                rs.getObject("artifact_type_id", UUID.class),
                rs.getObject("phase_id", UUID.class),
                rs.getString("artifact_type_code"),
                rs.getString("artifact_name"),
                rs.getString("parse_mode"),
                rs.getString("parse_status"),
                rs.getString("source_path"),
                rs.getString("content_hash"),
                rs.getString("schema_version"),
                rs.getObject("schema_valid", Boolean.class),
                rs.getObject("template_empty_flag", Boolean.class),
                readStringList(rs.getString("required_fields_missing")),
                rs.getString("parsed_summary"),
                rs.getString("parser_version"),
                rs.getObject("collected_at", OffsetDateTime.class)
        );
    }

    private ArtifactTypeLookup lookupArtifactType() {
        return jdbc.queryForObject("""
                        SELECT artifact_type_id, phase_id, artifact_type_code, artifact_name
                        FROM tbl_dim_artifact_type
                        WHERE artifact_type_code = :artifactTypeCode
                        """,
                new MapSqlParameterSource("artifactTypeCode", artifactTypeCode),
                (rs, rowNum) -> new ArtifactTypeLookup(
                        rs.getObject("artifact_type_id", UUID.class),
                        rs.getObject("phase_id", UUID.class),
                        rs.getString("artifact_type_code"),
                        rs.getString("artifact_name")));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private record ArtifactTypeLookup(
            UUID artifactTypeId,
            UUID phaseId,
            String artifactTypeCode,
            String artifactName
    ) {}
}
