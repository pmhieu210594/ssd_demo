package com.sdd.platform.infrastructure.persistence.adapter.docparse;

import com.sdd.platform.application.port.out.persistence.TestEvidencePersistencePort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class TestEvidenceJdbcAdapterTest {

    @Test
    void replacePlannedCoverage_replaces_all_planned_rows_for_ticket_not_only_one_snapshot() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TestEvidenceJdbcAdapter adapter = new TestEvidenceJdbcAdapter(jdbc);

        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        UUID firstSnapshotId = UUID.fromString("00000000-0000-0000-0000-00000000c101");
        UUID ticketId = UUID.fromString("00000000-0000-0000-0000-00000000c102");

        adapter.replacePlannedCoverage(firstSnapshotId, ticketId, "hash-1", List.of("AC-1", "AC-2"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, times(3)).update(sqlCaptor.capture(), paramsCaptor.capture());

        String sql = sqlCaptor.getAllValues().get(0);
        assertTrue(sql.contains("DELETE FROM tbl_fact_ac_test_coverage"));
        assertTrue(sql.contains("WHERE ticket_id = :ticketId"));
        assertTrue(sql.contains("test_run_id IS NULL"));
        assertEquals(ticketId, paramsCaptor.getAllValues().get(0).getValue("ticketId"));
    }

    @Test
    void upsertTestRun_reuses_existing_row_for_same_ticket() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TestEvidenceJdbcAdapter adapter = new TestEvidenceJdbcAdapter(jdbc);

        UUID ticketId = UUID.fromString("00000000-0000-0000-0000-00000000c201");
        UUID existingTestRunId = UUID.fromString("00000000-0000-0000-0000-00000000c202");
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(UUID.class)))
                .thenReturn(existingTestRunId);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        TestEvidencePersistencePort.TestRunRecord saved = adapter
                .upsertTestRun(new TestEvidencePersistencePort.TestRunRecord(
                        null,
                        UUID.fromString("00000000-0000-0000-0000-00000000c203"),
                        ticketId,
                        null,
                        null,
                        "trace-1",
                        "TEST_RESULTS",
                        "SUCCESS",
                        10,
                        8,
                        0,
                        2,
                        null,
                        null,
                        null,
                        null,
                        null));

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(UUID.class));
        verify(jdbc).update(anyString(), paramsCaptor.capture());

        assertEquals(existingTestRunId, saved.testRunId());
        assertEquals(existingTestRunId, paramsCaptor.getValue().getValue("testRunId"));
    }

    @Test
    void upsertTestRun_inserts_new_row_when_ticket_has_no_previous_run() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TestEvidenceJdbcAdapter adapter = new TestEvidenceJdbcAdapter(jdbc);

        UUID ticketId = UUID.fromString("00000000-0000-0000-0000-00000000c301");
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(UUID.class)))
                .thenThrow(new EmptyResultDataAccessException(1));
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        TestEvidencePersistencePort.TestRunRecord saved = adapter
                .upsertTestRun(new TestEvidencePersistencePort.TestRunRecord(
                        null,
                        UUID.fromString("00000000-0000-0000-0000-00000000c302"),
                        ticketId,
                        null,
                        null,
                        "trace-2",
                        "TEST_RESULTS",
                        "SUCCESS",
                        10,
                        9,
                        0,
                        1,
                        null,
                        null,
                        null,
                        null,
                        null));

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(UUID.class));
        verify(jdbc).update(anyString(), paramsCaptor.capture());

        UUID generatedTestRunId = (UUID) paramsCaptor.getValue().getValue("testRunId");
        assertEquals(generatedTestRunId, saved.testRunId());
        assertNotNull(generatedTestRunId);
    }

    @Test
    void upsertExecutedCoverageRows_writes_test_case_row_and_returns_saved_records() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TestEvidenceJdbcAdapter adapter = new TestEvidenceJdbcAdapter(jdbc);

        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        UUID testRunId = UUID.fromString("00000000-0000-0000-0000-00000000c401");
        UUID ticketId = UUID.fromString("00000000-0000-0000-0000-00000000c402");
        UUID repositoryId = UUID.fromString("00000000-0000-0000-0000-00000000c403");
        UUID artifactSnapshotId = UUID.fromString("00000000-0000-0000-0000-00000000c404");

        List<TestEvidencePersistencePort.ExecutedCoverageRecord> saved = adapter.upsertExecutedCoverageRows(List.of(
                new TestEvidencePersistencePort.ExecutedCoverageRecord(
                        null,
                        testRunId,
                        ticketId,
                        repositoryId,
                        null,
                        artifactSnapshotId,
                        "tc-ac-1",
                        "name-hash-1",
                        "AC-TEST-COVERAGE-1",
                        "FAILED",
                        "FAILED",
                        null,
                        "failure summary",
                        false,
                        "ac-hash-1",
                        OffsetDateTime.parse("2026-06-24T00:00:00Z"))));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, times(1)).update(sqlCaptor.capture(), paramsCaptor.capture());

        assertEquals(1, saved.size());
        assertEquals(testRunId, saved.getFirst().testRunId());
        assertEquals(ticketId, paramsCaptor.getValue().getValue("ticketId"));
        assertTrue(sqlCaptor.getValue().contains("tbl_fact_test_case"));
    }

    @Test
    void replacePlannedCoverage_withEmptyAcList_deletes_planned_rows_without_inserts() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TestEvidenceJdbcAdapter adapter = new TestEvidenceJdbcAdapter(jdbc);

        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        UUID snapshotId = UUID.fromString("00000000-0000-0000-0000-00000000c103");
        UUID ticketId = UUID.fromString("00000000-0000-0000-0000-00000000c104");

        adapter.replacePlannedCoverage(snapshotId, ticketId, "hash-2", List.of());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(sqlCaptor.capture(), paramsCaptor.capture());
        assertTrue(sqlCaptor.getValue().contains("DELETE FROM tbl_fact_ac_test_coverage"));
        assertEquals(ticketId, paramsCaptor.getValue().getValue("ticketId"));
        assertEquals(1, paramsCaptor.getAllValues().size());
    }

    @Test
    void replacePlannedCoverage_dedupes_duplicate_ac_keys() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TestEvidenceJdbcAdapter adapter = new TestEvidenceJdbcAdapter(jdbc);

        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        UUID snapshotId = UUID.fromString("00000000-0000-0000-0000-00000000c105");
        UUID ticketId = UUID.fromString("00000000-0000-0000-0000-00000000c106");

        adapter.replacePlannedCoverage(snapshotId, ticketId, "hash-3", List.of("AC-1", "AC-1", "AC-2"));

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, times(3)).update(anyString(), paramsCaptor.capture());

        List<MapSqlParameterSource> paramValues = paramsCaptor.getAllValues();
        assertEquals(ticketId, paramValues.get(0).getValue("ticketId"));

        String firstInsertAcKey = (String) paramValues.get(1).getValue("acKey");
        String secondInsertAcKey = (String) paramValues.get(2).getValue("acKey");
        assertTrue((firstInsertAcKey.equals("AC-1") && secondInsertAcKey.equals("AC-2"))
                || (firstInsertAcKey.equals("AC-2") && secondInsertAcKey.equals("AC-1")));
    }

    @Test
    void linkTestCasesToPlannedCoverage_avoids_duplicate_test_case_updates() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TestEvidenceJdbcAdapter adapter = new TestEvidenceJdbcAdapter(jdbc);

        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        UUID ticketId = UUID.fromString("00000000-0000-0000-0000-00000000c107");
        adapter.linkTestCasesToPlannedCoverage(ticketId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sqlCaptor.capture(), any(MapSqlParameterSource.class));
        assertTrue(sqlCaptor.getValue().contains("NOT EXISTS"));
    }

    @Test
    void updateExecutedCoverageFromJunction_maps_success_failed_and_unknown_statuses() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TestEvidenceJdbcAdapter adapter = new TestEvidenceJdbcAdapter(jdbc);

        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        UUID ticketId = UUID.fromString("00000000-0000-0000-0000-00000000c405");
        UUID testRunId = UUID.fromString("00000000-0000-0000-0000-00000000c406");
        UUID artifactSnapshotId = UUID.fromString("00000000-0000-0000-0000-00000000c407");

        adapter.updateExecutedCoverageFromJunction(ticketId, testRunId, artifactSnapshotId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(sqlCaptor.capture(), paramsCaptor.capture());
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("CASE tc.status::text"));
        assertTrue(sql.contains("WHEN 'SUCCESS' THEN 'PASSED'"));
        assertTrue(sql.contains("WHEN 'FAILED' THEN 'FAILED'"));
        assertTrue(sql.contains("ELSE 'UNKNOWN'"));
        assertEquals(ticketId, paramsCaptor.getValue().getValue("ticketId"));
        assertEquals(testRunId, paramsCaptor.getValue().getValue("testRunId"));
        assertEquals(artifactSnapshotId, paramsCaptor.getValue().getValue("artifactSnapshotId"));
    }

}