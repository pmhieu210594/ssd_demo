package com.sdd.platform.infrastructure.persistence.adapter;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TemplateUsageStatJdbcAdapterTest {

    @Test
    void recordCheck_upsertsWithMatchIncrementOfOneWhenMatched() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TemplateUsageStatJdbcAdapter adapter = new TemplateUsageStatJdbcAdapter(jdbc);
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-00000000d001");
        UUID repositoryId = UUID.fromString("00000000-0000-0000-0000-00000000d002");
        UUID phaseId = UUID.fromString("00000000-0000-0000-0000-00000000d003");

        adapter.recordCheck(projectId, repositoryId, phaseId, true);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(sqlCaptor.capture(), paramsCaptor.capture());

        String sql = sqlCaptor.getValue();
        MapSqlParameterSource params = paramsCaptor.getValue();
        assertTrue(sql.contains("tbl_fact_template_usage_stat"));
        assertTrue(sql.contains("ON CONFLICT (project_id, repository_id, phase_id)"));
        assertTrue(sql.contains("total_check_count = tbl_fact_template_usage_stat.total_check_count + 1"));
        assertEquals(projectId, params.getValue("projectId"));
        assertEquals(repositoryId, params.getValue("repositoryId"));
        assertEquals(phaseId, params.getValue("phaseId"));
        assertEquals(1, params.getValue("matchIncrement"));
    }

    @Test
    void recordCheck_upsertsWithMatchIncrementOfZeroWhenNotMatched() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TemplateUsageStatJdbcAdapter adapter = new TemplateUsageStatJdbcAdapter(jdbc);
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-00000000d001");
        UUID repositoryId = UUID.fromString("00000000-0000-0000-0000-00000000d002");
        UUID phaseId = UUID.fromString("00000000-0000-0000-0000-00000000d003");

        adapter.recordCheck(projectId, repositoryId, phaseId, false);

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), paramsCaptor.capture());
        assertEquals(0, paramsCaptor.getValue().getValue("matchIncrement"));
    }
}
