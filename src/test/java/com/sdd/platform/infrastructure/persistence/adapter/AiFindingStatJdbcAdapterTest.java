package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.AiFindingStatPort.AiFindingStatRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiFindingStatJdbcAdapterTest {

    @Test
    void upsert_writesAllCountsAndUsesTicketIdConflictTarget() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AiFindingStatJdbcAdapter adapter = new AiFindingStatJdbcAdapter(jdbc);
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-00000000a001");
        UUID repositoryId = UUID.fromString("00000000-0000-0000-0000-00000000a002");
        UUID ticketId = UUID.fromString("00000000-0000-0000-0000-00000000a003");
        AiFindingStatRecord record = new AiFindingStatRecord(
                projectId, repositoryId, ticketId, 1, 1, 2, 2, 2, 0, 2);

        adapter.upsert(record);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(sqlCaptor.capture(), paramsCaptor.capture());

        String sql = sqlCaptor.getValue();
        MapSqlParameterSource params = paramsCaptor.getValue();
        assertTrue(sql.contains("tbl_fact_ai_finding_stat"));
        assertTrue(sql.contains("ON CONFLICT (ticket_id) DO UPDATE SET"));
        assertTrue(sql.contains("blocker_major_resolved_count = EXCLUDED.blocker_major_resolved_count"));
        assertTrue(sql.contains("updated_at = now()"));
        assertEquals(projectId, params.getValue("projectId"));
        assertEquals(repositoryId, params.getValue("repositoryId"));
        assertEquals(ticketId, params.getValue("ticketId"));
        assertEquals(1, params.getValue("blockerMajorResolvedCount"));
        assertEquals(1, params.getValue("blockerMajorTotalCount"));
        assertEquals(2, params.getValue("aiReviewAdoptedCount"));
        assertEquals(2, params.getValue("aiReviewFindingTotalCount"));
        assertEquals(2, params.getValue("aiReviewValidCount"));
        assertEquals(0, params.getValue("aiReviewFalsePositiveCount"));
        assertEquals(2, params.getValue("aiReviewResolvedCount"));
    }

    @Test
    void upsert_passesNullForUnavailableCounts() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AiFindingStatJdbcAdapter adapter = new AiFindingStatJdbcAdapter(jdbc);
        AiFindingStatRecord record = new AiFindingStatRecord(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, null, 2, 2, 2, 0, 2);

        adapter.upsert(record);

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(org.mockito.ArgumentMatchers.anyString(), paramsCaptor.capture());
        assertNull(paramsCaptor.getValue().getValue("blockerMajorResolvedCount"));
        assertNull(paramsCaptor.getValue().getValue("blockerMajorTotalCount"));
    }
}
