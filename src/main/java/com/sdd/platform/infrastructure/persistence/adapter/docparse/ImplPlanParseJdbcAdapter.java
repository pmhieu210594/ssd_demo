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
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Primary
public class ImplPlanParseJdbcAdapter implements DocParsePersistencePort {

    private static final String SYSTEM_ACTOR = "SYSTEM";
    private static final String ARTIFACT_TYPE_CODE = "IMPL_PLAN";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ImplPlanParseJdbcAdapter(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public ParseSnapshot upsertSnapshot(ParseSnapshot snapshot) {
        ArtifactTypeLookup lookup = lookupArtifactType();
        String parsedSummaryJson = snapshot.parsedSummaryJson() == null || snapshot.parsedSummaryJson().isBlank()
                ? "{}"
                : snapshot.parsedSummaryJson();
        List<String> missingFields = snapshot.requiredFieldsMissing() == null ? List.of()
                : snapshot.requiredFieldsMissing();

        UUID id = jdbc.queryForObject("""
                INSERT INTO tbl_fact_artifact_snapshot (
                    ticket_id, repository_id, artifact_type_id, phase_id, source_path, source_url_hash,
                    exists_flag, content_hash, schema_version, schema_valid, template_empty_flag,
                    required_fields_missing, parsed_summary, parser_version,
                    source_updated_at, collected_at, connector_run_id,
                    created_at, created_by, updated_at, updated_by
                )
                VALUES (
                    :ticketId, :repositoryId, :artifactTypeId, :phaseId, :sourcePath, :sourceUrlHash,
                    :existsFlag, :contentHash, :schemaVersion, :schemaValid, :templateEmptyFlag,
                    CAST(:requiredFieldsMissing AS jsonb), CAST(:parsedSummary AS jsonb), :parserVersion,
                    :sourceUpdatedAt, :collectedAt, :connectorRunId, now(), :actor, now(), :actor
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
                    source_updated_at = EXCLUDED.source_updated_at,
                    collected_at = EXCLUDED.collected_at,
                    connector_run_id = EXCLUDED.connector_run_id,
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
                        .addValue("sourceUpdatedAt", snapshot.sourceUpdatedAt())
                        .addValue("collectedAt", snapshot.collectedAt())
                        .addValue("connectorRunId", snapshot.connectorRunId())
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
                snapshot.connectorRunId(),
                snapshot.sourceUpdatedAt(),
                snapshot.collectedAt());
    }

    @Override
    public void replaceSections(UUID snapshotId, UUID ticketId, List<ParseField> sections) {
        if (sections == null || sections.isEmpty()) {
            return;
        }
        String sectionType = sections.get(0).sectionType();
        jdbc.update("""
                DELETE FROM tbl_fact_artifact_parsed_section
                WHERE ticket_id = :ticketId
                  AND section_type = :sectionType
                """,
                new MapSqlParameterSource()
                        .addValue("ticketId", ticketId)
                        .addValue("sectionType", sectionType));
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
    public Optional<ParseSnapshot> findLatestSnapshot(UUID ticketId, String artifactTypeCode, ParseMode parseMode) {
        return jdbc.query("""
                SELECT s.artifact_snapshot_id,
                       s.ticket_id,
                       s.repository_id,
                       s.artifact_type_id,
                       s.phase_id,
                       t.artifact_type_code,
                       t.artifact_name,
                        s.source_path,
                       s.content_hash,
                       s.schema_version,
                       s.schema_valid,
                       s.template_empty_flag,
                       s.required_fields_missing,
                       s.parsed_summary,
                       s.parser_version,
                       s.source_updated_at,
                       s.connector_run_id,
                       s.collected_at
                FROM tbl_fact_artifact_snapshot s
                JOIN tbl_dim_artifact_type t ON t.artifact_type_id = s.artifact_type_id
                WHERE s.ticket_id = :ticketId
                  AND t.artifact_type_code = :artifactTypeCode
                ORDER BY s.collected_at DESC, s.artifact_snapshot_id DESC
                """,
                new MapSqlParameterSource()
                        .addValue("ticketId", ticketId)
                        .addValue("artifactTypeCode", artifactTypeCode)
                        .addValue("parseMode", parseMode.name()),
                this::mapSnapshot).stream()
                .filter(snapshot -> parseMode.name().equalsIgnoreCase(snapshot.parseMode()))
                .findFirst();
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
                        s.source_path,
                       s.content_hash,
                       s.schema_version,
                       s.schema_valid,
                       s.template_empty_flag,
                       s.required_fields_missing,
                       s.parsed_summary,
                       s.parser_version,
                       s.source_updated_at,
                       s.connector_run_id,
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
    public List<ParseSnapshot> findSnapshots(UUID ticketId, String artifactTypeCode, ParseMode parseMode, int limit) {
        int safeLimit = Math.max(Math.min(limit, 100), 1);
        return jdbc.query("""
                SELECT s.artifact_snapshot_id,
                       s.ticket_id,
                       s.repository_id,
                       s.artifact_type_id,
                       s.phase_id,
                       t.artifact_type_code,
                       t.artifact_name,
                        s.source_path,
                       s.content_hash,
                       s.schema_version,
                       s.schema_valid,
                       s.template_empty_flag,
                        s.required_fields_missing,
                        s.parsed_summary,
                        s.parser_version,
                        s.source_updated_at,
                        s.connector_run_id,
                        s.collected_at
                FROM tbl_fact_artifact_snapshot s
                JOIN tbl_dim_artifact_type t ON t.artifact_type_id = s.artifact_type_id
                WHERE s.ticket_id = :ticketId
                  AND t.artifact_type_code = :artifactTypeCode
                ORDER BY s.collected_at DESC, s.artifact_snapshot_id DESC
                """,
                new MapSqlParameterSource()
                        .addValue("ticketId", ticketId)
                        .addValue("artifactTypeCode", artifactTypeCode)
                        .addValue("parseMode", parseMode.name())
                        .addValue("limit", safeLimit),
                this::mapSnapshot).stream()
                .filter(snapshot -> parseMode.name().equalsIgnoreCase(snapshot.parseMode()))
                .limit(safeLimit)
                .toList();
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
                    project_id, repository_id, connector_run_id, source_type, source_ref,
                    missing_count, parse_error_count, schema_violation_count, freshness_delay_minutes,
                    error_summary, checked_at,
                    created_at, created_by, updated_at, updated_by
                )
                VALUES (
                    :projectId, :repositoryId, :connectorRunId, :sourceType, :sourceRef,
                    :missingCount, :parseErrorCount, :schemaViolationCount, :freshnessDelayMinutes,
                    :errorSummary, :checkedAt,
                    now(), :actor, now(), :actor
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
                    , checked_at = EXCLUDED.checked_at
                """,
                new MapSqlParameterSource()
                        .addValue("projectId", quality.projectId())
                        .addValue("repositoryId", quality.repositoryId())
                        .addValue("connectorRunId", quality.connectorRunId())
                        .addValue("sourceType", quality.sourceType())
                        .addValue("sourceRef", quality.sourceRef())
                        .addValue("missingCount", quality.missingCount())
                        .addValue("parseErrorCount", quality.parseErrorCount())
                        .addValue("schemaViolationCount", quality.schemaViolationCount())
                        .addValue("freshnessDelayMinutes", quality.freshnessDelayMinutes())
                        .addValue("errorSummary", quality.errorSummary())
                        .addValue("checkedAt", quality.checkedAt())
                        .addValue("actor", SYSTEM_ACTOR));
    }

    private ParseSnapshot mapSnapshot(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        String parsedSummaryJson = rs.getString("parsed_summary");
        return new ParseSnapshot(
                rs.getObject("artifact_snapshot_id", UUID.class),
                rs.getObject("ticket_id", UUID.class),
                rs.getObject("repository_id", UUID.class),
                rs.getObject("artifact_type_id", UUID.class),
                rs.getObject("phase_id", UUID.class),
                rs.getString("artifact_type_code"),
                rs.getString("artifact_name"),
                parseJsonValue(parsedSummaryJson, "parseMode", "DRAFT"),
                parseJsonValue(parsedSummaryJson, "parseStatus", "PARSE_ERROR"),
                rs.getString("source_path"),
                rs.getString("content_hash"),
                rs.getObject("schema_version", Integer.class),
                rs.getObject("schema_valid", Boolean.class),
                rs.getObject("template_empty_flag", Boolean.class),
                readStringList(rs.getString("required_fields_missing")),
                parsedSummaryJson,
                rs.getString("parser_version"),
                rs.getObject("connector_run_id", UUID.class),
                rs.getObject("source_updated_at", java.time.OffsetDateTime.class),
                rs.getObject("collected_at", java.time.OffsetDateTime.class));
    }

    private ArtifactTypeLookup lookupArtifactType() {
        return jdbc.queryForObject("""
                SELECT artifact_type_id, phase_id, artifact_type_code, artifact_name
                FROM tbl_dim_artifact_type
                WHERE artifact_type_code = :artifactTypeCode
                """,
                new MapSqlParameterSource("artifactTypeCode", ARTIFACT_TYPE_CODE),
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
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String parseJsonValue(String json, String fieldName, String fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readTree(json).path(fieldName).asText(fallback);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private record ArtifactTypeLookup(
            UUID artifactTypeId,
            UUID phaseId,
            String artifactTypeCode,
            String artifactName) {
    }
}
