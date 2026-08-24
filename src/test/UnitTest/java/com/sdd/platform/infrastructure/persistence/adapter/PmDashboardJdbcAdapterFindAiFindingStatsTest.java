package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.AiFindingStatsRow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PmDashboardJdbcAdapterFindAiFindingStatsTest {

    @Test
    void findAiFindingStats_scopesBothJoinedFactRowsAndDimensionRowToAuthorizedProject() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        DashboardProjectAccessJdbcAdapter projectAccess = mock(DashboardProjectAccessJdbcAdapter.class);
        PmDashboardJdbcAdapter adapter = new PmDashboardJdbcAdapter(jdbc, projectAccess);
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-00000000f001");
        UUID repositoryId = UUID.fromString("00000000-0000-0000-0000-00000000f002");

        adapter.findAiFindingStats(projectId, repositoryId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));

        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("FROM tbl_dim_repository r"));
        assertTrue(sql.contains("LEFT JOIN tbl_fact_ai_finding_stat s"));

        int whereIndex = sql.indexOf("WHERE");
        assertTrue(whereIndex >= 0, "expected a WHERE clause scoping repository_id");
        String beforeWhere = sql.substring(0, whereIndex);
        String fromWhere = sql.substring(whereIndex);
        assertTrue(beforeWhere.contains("s.project_id = :projectId"),
                "fact rows must still be tenant-scoped inside the JOIN ON clause");
        assertTrue(fromWhere.contains("r.project_id = :projectId"),
                "dimension row itself must also be scoped to the authorized project, "
                        + "so a repository from another project cannot be looked up by id alone");

        MapSqlParameterSource params = paramsCaptor.getValue();
        assertEquals(projectId, params.getValue("projectId"));
        assertEquals(repositoryId, params.getValue("repositoryId"));
    }

    @Test
    void findAiFindingStats_returnsEmptyForRepositoryBelongingToAnotherProject() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        DashboardProjectAccessJdbcAdapter projectAccess = mock(DashboardProjectAccessJdbcAdapter.class);
        PmDashboardJdbcAdapter adapter = new PmDashboardJdbcAdapter(jdbc, projectAccess);
        UUID authorizedProjectId = UUID.randomUUID();
        UUID otherProjectsRepositoryId = UUID.randomUUID();
        when(jdbc.query(any(String.class), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        Optional<AiFindingStatsRow> result =
                adapter.findAiFindingStats(authorizedProjectId, otherProjectsRepositoryId);

        assertEquals(Optional.empty(), result);
    }

    @Test
    void findAiFindingStats_mapsSummedCountsAndRepositoryName() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        DashboardProjectAccessJdbcAdapter projectAccess = mock(DashboardProjectAccessJdbcAdapter.class);
        PmDashboardJdbcAdapter adapter = new PmDashboardJdbcAdapter(jdbc, projectAccess);
        UUID projectId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        AiFindingStatsRow row = new AiFindingStatsRow(repositoryId, "widget", 1, 1, 2, 2, 2, 0, 2);
        when(jdbc.query(any(String.class), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(row));

        Optional<AiFindingStatsRow> result = adapter.findAiFindingStats(projectId, repositoryId);

        assertEquals(Optional.of(row), result);
    }

    @Test
    void findAiFindingStats_returnsEmptyWhenNoRowMatches() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        DashboardProjectAccessJdbcAdapter projectAccess = mock(DashboardProjectAccessJdbcAdapter.class);
        PmDashboardJdbcAdapter adapter = new PmDashboardJdbcAdapter(jdbc, projectAccess);
        when(jdbc.query(any(String.class), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        Optional<AiFindingStatsRow> result = adapter.findAiFindingStats(UUID.randomUUID(), UUID.randomUUID());

        assertEquals(Optional.empty(), result);
    }
}
