package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.usecase.traceability.TraceabilityModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TraceabilityJdbcAdapterTest {

    private NamedParameterJdbcTemplate jdbc;
    private TraceabilityJdbcAdapter adapter;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        adapter = new TraceabilityJdbcAdapter(jdbc);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
    }

    @Test
    void queries_existing_tbl_tables_only() {
        UUID ticketId = UUID.fromString("8bb0b3c7-90ce-4b1b-82c0-1bbf9c17ef61");

        adapter.findTicket(ticketId);
        adapter.findArtifacts(ticketId);
        adapter.findPullRequests(ticketId);
        adapter.findCommits(ticketId);
        adapter.findCiRuns(ticketId);
        adapter.findTraceabilityLinks(ticketId);
        adapter.findEvidenceEvents(ticketId);
        adapter.findParsedSections(ticketId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(8)).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));

        List<String> sqlStatements = sqlCaptor.getAllValues();
        assertTrue(sqlStatements.stream().anyMatch(sql -> sql.contains("FROM tbl_dim_ticket")));
        assertTrue(sqlStatements.stream().anyMatch(sql -> sql.contains("FROM tbl_fact_artifact_snapshot")));
        assertTrue(sqlStatements.stream().anyMatch(sql -> sql.contains("FROM tbl_fact_pull_request pr")));
        assertTrue(sqlStatements.stream().anyMatch(sql -> sql.contains("JOIN tbl_fact_commit c")));
        assertTrue(sqlStatements.stream().anyMatch(sql -> sql.contains("FROM tbl_fact_ci_run")));
        assertTrue(sqlStatements.stream().anyMatch(sql -> sql.contains("FROM tbl_fact_traceability_link")));
        assertTrue(sqlStatements.stream().anyMatch(sql -> sql.contains("FROM tbl_fact_evidence_event")));
        assertTrue(sqlStatements.stream().anyMatch(sql -> sql.contains("FROM tbl_fact_artifact_parsed_section")));
    }
}
