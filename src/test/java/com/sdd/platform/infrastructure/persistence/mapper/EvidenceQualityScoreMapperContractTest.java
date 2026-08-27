package com.sdd.platform.infrastructure.persistence.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.LineageEntry;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreCriterion;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvidenceQualityScoreMapperContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void score_result_row_mapper_rehydrates_persisted_contract() throws Exception {
        UUID scoreId = UUID.fromString("00000000-0000-0000-0000-00000000e101");
        UUID metricValueId = UUID.fromString("00000000-0000-0000-0000-00000000e102");
        UUID ticketId = UUID.fromString("00000000-0000-0000-0000-00000000e103");
        UUID sourceId = UUID.fromString("00000000-0000-0000-0000-00000000e104");
        OffsetDateTime calculatedAt = OffsetDateTime.parse("2026-06-24T00:00:00Z");

        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("evidence_quality_score_id", UUID.class)).thenReturn(scoreId);
        when(rs.getObject("metric_value_id", UUID.class)).thenReturn(metricValueId);
        when(rs.getObject("ticket_id", UUID.class)).thenReturn(ticketId);
        when(rs.getBigDecimal("score")).thenReturn(new BigDecimal("86.50"));
        when(rs.getString("score_band")).thenReturn("GOOD");
        when(rs.getString("breakdown")).thenReturn(objectMapper.writeValueAsString(List.of(
                new ScoreCriterion(
                        "spec_pack_ac_numbering",
                        "spec-pack.md exists and has numbered ACs",
                        new BigDecimal("15.00"),
                        new BigDecimal("15.00"),
                        "complete",
                        List.of("tbl_fact_artifact_snapshot:" + sourceId)
                ))));
        when(rs.getString("missing_items")).thenReturn("[\"test-results.md\"]");
        when(rs.getString("parse_errors")).thenReturn("[\"TEST_RESULTS:PARSE_ERROR\"]");
        when(rs.getString("trace_ids")).thenReturn("[\"" + ticketId + "\",\"" + sourceId + "\"]");
        when(rs.getString("score_rule_version")).thenReturn("v0");
        when(rs.getString("snapshot_state")).thenReturn("final");
        when(rs.getObject("calculated_at", OffsetDateTime.class)).thenReturn(calculatedAt);
        when(rs.getBigDecimal("spec_score")).thenReturn(new BigDecimal("15.00"));
        when(rs.getBigDecimal("plan_score")).thenReturn(new BigDecimal("10.00"));
        when(rs.getBigDecimal("review_score")).thenReturn(new BigDecimal("10.00"));
        when(rs.getBigDecimal("self_review_score")).thenReturn(new BigDecimal("15.00"));
        when(rs.getBigDecimal("test_score")).thenReturn(new BigDecimal("10.00"));
        when(rs.getBigDecimal("ci_score")).thenReturn(new BigDecimal("5.00"));
        when(rs.getBigDecimal("blackbox_score")).thenReturn(new BigDecimal("5.00"));
        when(rs.getBigDecimal("report_score")).thenReturn(new BigDecimal("10.00"));
        when(rs.getString("lineage")).thenReturn(objectMapper.writeValueAsString(List.of(
                new LineageEntry(
                        "tbl_fact_artifact_snapshot",
                        sourceId.toString(),
                        "hash-1",
                        "spec_pack_ac_numbering"
                ))));

        ScoreResult result = EvidenceQualityScoreMapper.scoreResultRowMapper(objectMapper).mapRow(rs, 0);

        assertEquals(scoreId, result.evidenceQualityScoreId());
        assertEquals(metricValueId, result.metricValueId());
        assertEquals(ticketId, result.ticketId());
        assertEquals(new BigDecimal("86.50"), result.score());
        assertEquals("Good", result.band());
        assertEquals(1, result.breakdown().size());
        assertEquals(List.of("test-results.md"), result.missing());
        assertEquals(List.of("TEST_RESULTS:PARSE_ERROR"), result.parseErrors());
        assertEquals(List.of(ticketId.toString(), sourceId.toString()), result.traceIds());
        assertEquals("v0", result.scoreRuleVersion());
        assertEquals("final", result.snapshotState());
        assertEquals(calculatedAt, result.calculatedAt());
        assertNotNull(result.lineage());
        assertEquals(1, result.lineage().size());
    }
}
