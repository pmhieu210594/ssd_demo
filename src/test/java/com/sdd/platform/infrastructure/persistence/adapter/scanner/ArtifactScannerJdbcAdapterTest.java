package com.sdd.platform.infrastructure.persistence.adapter.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArtifactScannerJdbcAdapterTest {

    @Test
    void insertSnapshot_preserves_existing_parse_summary_on_conflict() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ArtifactScannerJdbcAdapter adapter = new ArtifactScannerJdbcAdapter(jdbc, objectMapper);

        UUID snapshotId = UUID.fromString("00000000-0000-0000-0000-00000000a101");
        ArtifactSnapshot snapshot = new ArtifactSnapshot(
                snapshotId,
                UUID.fromString("00000000-0000-0000-0000-00000000a102"),
                UUID.fromString("00000000-0000-0000-0000-00000000a103"),
                UUID.fromString("00000000-0000-0000-0000-00000000a104"),
                "AC-001",
                "OPEN",
                OffsetDateTime.parse("2026-06-25T00:00:00Z"),
                UUID.fromString("00000000-0000-0000-0000-00000000a105"),
                UUID.fromString("00000000-0000-0000-0000-00000000a106"),
                "SPEC_PACK",
                "spec-pack",
                "spec-pack.md",
                true,
                "CHANGE",
                "docs/changes/AC-001/spec-pack.md",
                true,
                "hash-1",
                123L,
                OffsetDateTime.parse("2026-06-25T00:00:00Z"),
                false,
                false,
                "FOUND",
                null,
                OffsetDateTime.of(2026, 6, 25, 1, 0, 0, 0, ZoneOffset.UTC)
        );

        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), any(Class.class))).thenReturn(snapshotId);

        adapter.insertSnapshot(snapshot);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), any(Class.class));

        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("parsed_summary = COALESCE(tbl_fact_artifact_snapshot.parsed_summary, EXCLUDED.parsed_summary)"));
        assertTrue(sql.contains("required_fields_missing = COALESCE(tbl_fact_artifact_snapshot.required_fields_missing, EXCLUDED.required_fields_missing)"));
        assertEquals("{}", paramsCaptor.getValue().getValue("parsedSummary"));
    }
}
