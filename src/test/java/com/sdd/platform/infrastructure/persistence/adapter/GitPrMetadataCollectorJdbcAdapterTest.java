package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.PullRequestUpsert;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.TicketScope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitPrMetadataCollectorJdbcAdapterTest {

    @Test
    void upsertMinimalTicket_persists_created_by_from_collector_actor() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        GitPrMetadataCollectorJdbcAdapter adapter = new GitPrMetadataCollectorJdbcAdapter(jdbc);

        UUID ticketId = UUID.fromString("00000000-0000-0000-0000-00000000c201");
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-00000000c202");

        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(UUID.class))).thenReturn(ticketId);

        TicketScope saved = adapter.upsertMinimalTicket(
                projectId,
                "ABC-123",
                "ABC-123 Fix failing webhook",
                "OPEN",
                OffsetDateTime.parse("2026-06-17T01:00:00Z"),
                "p_octocat");

        assertEquals(ticketId, saved.ticketId());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), eq(UUID.class));

        String sql = sqlCaptor.getValue();
        MapSqlParameterSource params = paramsCaptor.getValue();
        assertTrue(sql.contains("created_by"));
        assertTrue(sql.contains("updated_by"));
        assertEquals("p_octocat", params.getValue("createdBy"));
    }

    @Test
    void upsertPullRequest_persists_author_display_name_and_author_member_key() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        GitPrMetadataCollectorJdbcAdapter adapter = new GitPrMetadataCollectorJdbcAdapter(jdbc);

        UUID prId = UUID.fromString("00000000-0000-0000-0000-00000000c101");
        UUID repositoryId = UUID.fromString("00000000-0000-0000-0000-00000000c102");
        UUID ticketId = UUID.fromString("00000000-0000-0000-0000-00000000c103");
        UUID authorMemberKey = UUID.fromString("00000000-0000-0000-0000-00000000c104");

        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(UUID.class))).thenReturn(prId);

        PullRequestUpsert request = new PullRequestUpsert(
                repositoryId,
                ticketId,
                123,
                "123",
                "ABC-123 Fix failing webhook",
                "desc-hash",
                "OPEN",
                "feature/ABC-123",
                "main",
                OffsetDateTime.parse("2026-06-17T00:00:00Z"),
                OffsetDateTime.parse("2026-06-17T01:00:00Z"),
                null,
                null,
                "Octo Cat",
                authorMemberKey,
                "[\"bug\"]",
                "ABC-123",
                "https://github.com/acme/widget/pull/123",
                "APPROVED",
                OffsetDateTime.parse("2026-06-17T01:30:00Z")
        );

        UUID savedId = adapter.upsertPullRequest(request);

        assertEquals(prId, savedId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), eq(UUID.class));

        String sql = sqlCaptor.getValue();
        MapSqlParameterSource params = paramsCaptor.getValue();
        assertTrue(sql.contains("author_display_name"));
        assertEquals("Octo Cat", params.getValue("authorDisplayName"));
        assertEquals(authorMemberKey, params.getValue("authorMemberKey"));
    }
}
