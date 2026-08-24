package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.usecase.dataopsdashboard.DataOpsDashboardModels;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataOpsDashboardJdbcAdapterTest {

    private NamedParameterJdbcTemplate jdbc;
    private DataOpsDashboardJdbcAdapter adapter;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        adapter = new DataOpsDashboardJdbcAdapter(jdbc, new ObjectMapper(), mock(DashboardProjectAccessJdbcAdapter.class));
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
    }

    @Test
    void summary_builds_parse_error_sql_without_trailing_semicolon() {
        DataOpsDashboardModels.DataOpsDashboardFilter filter = new DataOpsDashboardModels.DataOpsDashboardFilter(
                null, null, null, null, null, 1, 20
        );

        DataOpsDashboardModels.DataOpsDashboardSummary summary = adapter.findSummary(filter);

        assertEquals(0L, summary.connectorFailureCount());
        assertEquals(0L, summary.parseErrorCount());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(5)).queryForObject(sqlCaptor.capture(), any(MapSqlParameterSource.class), eq(Long.class));

        List<String> sqlStatements = sqlCaptor.getAllValues();
        String parseErrorSql = sqlStatements.get(1);
        String missingEvidenceSql = sqlStatements.get(2);
        String brokenLinksSql = sqlStatements.get(4);

        assertFalse(parseErrorSql.contains(";"));
        assertTrue(parseErrorSql.contains("WITH latest_run AS"));
        assertTrue(parseErrorSql.contains("FROM unified_rows"));
        assertTrue(parseErrorSql.contains("unified_rows.parse_error_count > 0"));
        assertTrue(missingEvidenceSql.contains("unified_rows.missing_evidence_count > 0"));
        assertTrue(brokenLinksSql.contains("filtered_projects AS"));
        assertTrue(brokenLinksSql.contains("tl.confidence_level = 'LOW'"));
    }

    @Test
    void summary_applies_connector_filters_to_all_metrics() {
        DataOpsDashboardModels.DataOpsDashboardFilter filter = new DataOpsDashboardModels.DataOpsDashboardFilter(
                null, null, "ci run metadata", null, null, 1, 20
        );

        adapter.findSummary(filter);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(5)).queryForObject(sqlCaptor.capture(), any(MapSqlParameterSource.class), eq(Long.class));

        List<String> sqlStatements = sqlCaptor.getAllValues();
        sqlStatements.forEach(sql -> assertTrue(sql.contains("connectorName")));
    }

    @Test
    void connectors_derive_repository_from_latest_connector_run() {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        DataOpsDashboardModels.DataOpsDashboardFilter filter = new DataOpsDashboardModels.DataOpsDashboardFilter(
                null, null, null, null, null, 1, 20
        );

        adapter.findConnectors(filter);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));

        String dataSql = sqlCaptor.getValue();
        assertTrue(dataSql.contains("WITH latest_run AS"));
        assertTrue(dataSql.contains("COALESCE(sc.repository_id, lr.repository_id)"));
        assertTrue(dataSql.contains("COALESCE(sc.project_id, r.project_id) AS project_id"));
        assertTrue(dataSql.contains("LEFT JOIN latest_run lr ON lr.connector_id = sc.connector_id"));
        assertTrue(dataSql.contains("LEFT JOIN tbl_dim_project p"));
    }

    @Test
    void connectors_keep_projectless_source_connectors_visible() {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        DataOpsDashboardModels.DataOpsDashboardFilter filter = new DataOpsDashboardModels.DataOpsDashboardFilter(
                null, null, null, null, null, 1, 20
        );

        adapter.findConnectors(filter);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));

        String dataSql = sqlCaptor.getValue();
        assertTrue(dataSql.contains("COALESCE(sc.project_id, r.project_id) AS project_id"));
        assertTrue(dataSql.contains("LEFT JOIN tbl_dim_project p"));
        assertTrue(dataSql.contains("LEFT JOIN tbl_dim_repository r"));
    }
}
