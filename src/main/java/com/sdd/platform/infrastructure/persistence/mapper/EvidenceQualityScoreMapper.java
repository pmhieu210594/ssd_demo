package com.sdd.platform.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ArtifactSignal;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.CiSignal;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.LineageEntry;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ReviewSignal;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreCriterion;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreResult;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.TestSignal;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.TraceabilitySignal;
import com.sdd.platform.application.usecase.quality.ScoreThresholdConfigService;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EvidenceQualityScoreMapper {

    public static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    public static final TypeReference<List<ScoreCriterion>> SCORE_CRITERION_LIST = new TypeReference<>() {};
    public static final TypeReference<List<LineageEntry>> LINEAGE_LIST = new TypeReference<>() {};

    private EvidenceQualityScoreMapper() {
    }

    public static RowMapper<ArtifactSignal> artifactSignalRowMapper(ObjectMapper objectMapper) {
        return (rs, rowNum) -> new ArtifactSignal(
                rs.getObject("artifact_snapshot_id", UUID.class),
                rs.getString("artifact_type_code"),
                rs.getString("default_file_name"),
                rs.getBoolean("exists_flag"),
                rs.getBoolean("template_empty_flag"),
                rs.getString("parse_status"),
                rs.getString("parser_version"),
                rs.getObject("collected_at", OffsetDateTime.class),
                toInteger(rs.getObject("section_count")),
                toInteger(rs.getObject("table_count")),
                toInteger(rs.getObject("ac_count")),
                toInteger(rs.getObject("ac_valid_format_count")),
                rs.getString("final_verdict"),
                readStringList(objectMapper, rs.getString("required_fields_missing")),
                readStringList(objectMapper, rs.getString("parse_errors")),
                Collections.emptyMap(),
                readObjectMap(objectMapper, rs.getString("parsed_summary")),
                rs.getString("content_hash")
        );
    }

    public static RowMapper<ReviewSignal> reviewSignalRowMapper() {
        return (rs, rowNum) -> new ReviewSignal(
                rs.getObject("review_id", UUID.class),
                rs.getBoolean("present_flag"),
                rs.getString("review_state"),
                rs.getInt("comment_count"),
                rs.getInt("finding_count"),
                rs.getObject("collected_at", OffsetDateTime.class),
                List.of(nonBlank(rs.getString("review_id"), "review:" + rs.getString("review_id")))
        );
    }

    public static RowMapper<CiSignal> ciSignalRowMapper() {
        return (rs, rowNum) -> new CiSignal(
                rs.getObject("ci_run_id", UUID.class),
                rs.getBoolean("present_flag"),
                rs.getString("status"),
                rs.getString("ci_url"),
                rs.getString("external_run_id"),
                1,
                hasAnyText(rs.getString("ci_url"), rs.getString("external_run_id")) ? 1 : 0,
                rs.getObject("collected_at", OffsetDateTime.class),
                List.of(nonBlank(rs.getString("ci_run_id"), "ci:" + rs.getString("ci_run_id")))
        );
    }

    public static RowMapper<TestSignal> testSignalRowMapper() {
        return (rs, rowNum) -> new TestSignal(
                rs.getInt("test_run_count"),
                rs.getInt("test_case_count"),
                rs.getInt("passed_count"),
                rs.getInt("failed_count"),
                rs.getInt("skipped_count"),
                rs.getInt("ac_coverage_count"),
                rs.getInt("distinct_covered_ac_count"),
                rs.getString("status"),
                rs.getBoolean("present_flag"),
                rs.getObject("collected_at", OffsetDateTime.class),
                readStringList(null, rs.getString("source_refs"))
        );
    }

    public static RowMapper<TraceabilitySignal> traceabilitySignalRowMapper() {
        return (rs, rowNum) -> new TraceabilitySignal(
                rs.getInt("link_count"),
                rs.getInt("required_link_count"),
                rs.getInt("present_link_count"),
                rs.getBoolean("chain_complete"),
                readStringList(null, rs.getString("link_types")),
                readStringList(null, rs.getString("source_refs")),
                rs.getObject("collected_at", OffsetDateTime.class)
        );
    }

    public static RowMapper<ScoreResult> scoreResultRowMapper(ObjectMapper objectMapper, ScoreThresholdConfigService scoreThresholdConfigService) {
        return (rs, rowNum) -> new ScoreResult(
                rs.getObject("evidence_quality_score_id", UUID.class),
                rs.getObject("metric_value_id", UUID.class),
                rs.getObject("ticket_id", UUID.class),
                rs.getBigDecimal("score"),
                readScoreCriteria(objectMapper, rs.getString("breakdown")),
                readStringList(objectMapper, rs.getString("missing_items")),
                readStringList(objectMapper, safeGetString(rs, "parse_errors")),
                readStringList(objectMapper, safeGetString(rs, "trace_ids")),
                rs.getString("score_rule_version"),
                rs.getString("snapshot_state"),
                rs.getObject("calculated_at", OffsetDateTime.class),
                rs.getBigDecimal("spec_score"),
                rs.getBigDecimal("plan_score"),
                rs.getBigDecimal("review_score"),
                rs.getBigDecimal("self_review_score"),
                rs.getBigDecimal("test_score"),
                rs.getBigDecimal("ci_score"),
                rs.getBigDecimal("blackbox_score"),
                rs.getBigDecimal("report_score"),
                readLineage(objectMapper, rs.getString("lineage"))
        );
    }

    public static List<ScoreCriterion> readScoreCriteria(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            if (objectMapper == null) {
                return List.of();
            }
            List<ScoreCriterion> values = objectMapper.readValue(json, SCORE_CRITERION_LIST);
            return values == null ? List.of() : values;
        } catch (Exception ex) {
            return List.of();
        }
    }

    public static List<LineageEntry> readLineage(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            if (objectMapper == null) {
                return List.of();
            }
            List<LineageEntry> values = objectMapper.readValue(json, LINEAGE_LIST);
            return values == null ? List.of() : values;
        } catch (Exception ex) {
            return List.of();
        }
    }

    public static List<String> readStringList(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            if (objectMapper == null) {
                return List.of();
            }
            List<String> values = objectMapper.readValue(json, STRING_LIST);
            return values == null ? List.of() : values;
        } catch (Exception ex) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Boolean> readBooleanMap(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank() || objectMapper == null) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readObjectMap(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank() || objectMapper == null) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    public static String toJson(ObjectMapper objectMapper, Object value) {
        if (objectMapper == null || value == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private static int toInteger(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean hasAnyText(String... values) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static String safeGetString(ResultSet rs, String columnLabel) {
        try {
            return rs.getString(columnLabel);
        } catch (SQLException ex) {
            return "[]";
        }
    }

}
