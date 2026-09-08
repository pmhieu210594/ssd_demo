package com.sdd.platform.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.LineageEntry;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreCriterion;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreResult;
import com.sdd.platform.application.usecase.quality.ScoreThresholdConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceQualityScoreRepositoryAdapterTest {

    @Test
    void save_persists_metric_value_score_row_and_lineage_with_trimmed_actor() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        EvidenceQualityScoreRepositoryAdapter adapter = new EvidenceQualityScoreRepositoryAdapter(jdbc, objectMapper,
                mock(ScoreThresholdConfigService.class));

        UUID metricId = UUID.fromString("00000000-0000-0000-0000-00000000f101");
        UUID ticketId = UUID.fromString("00000000-0000-0000-0000-00000000f102");
        UUID scoreId = UUID.fromString("00000000-0000-0000-0000-00000000f103");
        UUID metricValueId = UUID.fromString("00000000-0000-0000-0000-00000000f104");
        UUID lineageSourceId = UUID.fromString("00000000-0000-0000-0000-00000000f105");
        OffsetDateTime calculatedAt = OffsetDateTime.parse("2026-06-24T01:00:00Z");

        when(jdbc.query(
                contains("tbl_dim_metric_definition"),
                any(MapSqlParameterSource.class),
                any(RowMapper.class))).thenReturn(List.of(metricId));
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        ScoreResult result = new ScoreResult(
                scoreId,
                metricValueId,
                ticketId,
                new BigDecimal("86.50"),
                List.of(new ScoreCriterion(
                        "spec_pack_ac_numbering",
                        "spec-pack.md exists and has numbered ACs",
                        new BigDecimal("15.00"),
                        new BigDecimal("15.00"),
                        "complete",
                        List.of("tbl_fact_artifact_snapshot:" + lineageSourceId))),
                List.of("test-results.md"),
                List.of("TEST_RESULTS:PARSE_ERROR"),
                List.of(ticketId.toString(), lineageSourceId.toString()),
                "v0",
                "final",
                calculatedAt,
                new BigDecimal("15.00"),
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                new BigDecimal("15.00"),
                new BigDecimal("10.00"),
                new BigDecimal("5.00"),
                new BigDecimal("5.00"),
                new BigDecimal("10.00"),
                List.of(new LineageEntry(
                        "tbl_fact_artifact_snapshot",
                        lineageSourceId.toString(),
                        "hash-1",
                        "spec_pack_ac_numbering")));

        ScoreResult saved = adapter.save(result, "  reviewer@example.com  ");

        assertEquals(scoreId, saved.evidenceQualityScoreId());
        assertEquals(metricValueId, saved.metricValueId());
        assertNotNull(saved);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, times(3)).update(sqlCaptor.capture(), paramsCaptor.capture());

        List<String> sqls = sqlCaptor.getAllValues();
        List<MapSqlParameterSource> params = paramsCaptor.getAllValues();

        assertEquals(3, sqls.size());
        assertEquals(3, params.size());
        assertEquals(true, sqls.get(0).contains("tbl_fact_metric_value"));
        assertEquals(true, sqls.get(1).contains("tbl_fact_evidence_quality_score"));
        assertEquals(true, sqls.get(2).contains("tbl_fact_metric_input_lineage"));

        assertEquals("EVIDENCE_QUALITY_SCORE", params.get(0).getValue("metricCode"));
        assertEquals("v0", params.get(0).getValue("definitionVersion"));
        assertEquals("reviewer@example.com", params.get(0).getValue("createdBy"));
        assertEquals("v0", params.get(1).getValue("scoreRuleVersion"));
        assertEquals("reviewer@example.com", params.get(1).getValue("createdBy"));
        assertEquals("tbl_fact_artifact_snapshot", params.get(2).getValue("inputTable"));
        assertEquals(lineageSourceId.toString(), params.get(2).getValue("inputRecordId"));
        assertEquals("spec_pack_ac_numbering", params.get(2).getValue("contributionType"));
    }

    @Test
    void loadArtifactSignal_prefers_latest_snapshot_by_timestamp_not_summary_presence() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        EvidenceQualityScoreRepositoryAdapter adapter = new EvidenceQualityScoreRepositoryAdapter(jdbc, objectMapper,
                mock(ScoreThresholdConfigService.class));

        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        Method method = EvidenceQualityScoreRepositoryAdapter.class.getDeclaredMethod(
                "loadArtifactSignal",
                UUID.class,
                String.class,
                List.class);
        method.setAccessible(true);

        UUID ticketId = UUID.fromString("00000000-0000-0000-0000-00000000f201");
        method.invoke(adapter, ticketId, "SPEC_PACK", List.of("PHẠM_VI", "TRONG_PHẠM_VI", "NGOÀI_PHẠM_VI",
                "CÁC_VẤN_ĐỀ_MỞ", "RỦI_RO"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        String sql = sqlCaptor.getValue();
        assertEquals(true, sql.contains("ORDER BY"));
        assertEquals(true, sql.contains("s.collected_at DESC"));
        assertEquals(true, sql.contains("s.created_at DESC"));
        assertEquals(false, sql.contains("CASE"));
    }

    @Test
    void loadTestSignal_ignores_planned_coverage_rows_when_counting_executed_coverage() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        EvidenceQualityScoreRepositoryAdapter adapter = new EvidenceQualityScoreRepositoryAdapter(jdbc, objectMapper,
                mock(ScoreThresholdConfigService.class));

        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        Method method = EvidenceQualityScoreRepositoryAdapter.class.getDeclaredMethod("loadTestSignal", UUID.class);
        method.setAccessible(true);

        UUID ticketId = UUID.fromString("00000000-0000-0000-0000-00000000f301");
        method.invoke(adapter, ticketId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        String sql = sqlCaptor.getValue();
        assertEquals(true, sql.contains("latest_test_case"));
        assertEquals(true, sql.contains("FROM tbl_fact_test_case tc"));
        assertEquals(true, sql.contains("JOIN latest_test_run l ON l.test_run_id = tc.test_run_id"));
        assertEquals(true, sql.contains("tc.status::text = 'FAILED'"));
        assertEquals(true, sql.contains("tc.status::text <> 'FAILED'"));
        assertEquals(true, sql.contains("FROM tbl_fact_ac_test_coverage ac"));
    }
}
