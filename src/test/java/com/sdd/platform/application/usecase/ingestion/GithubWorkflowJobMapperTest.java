package com.sdd.platform.application.usecase.ingestion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GithubWorkflowJobMapperTest {

    @Test
    void normalizeStatus_maps_known_statuses_and_conclusions() {
        assertEquals("SUCCESS", GithubWorkflowJobMapper.normalizeStatus("completed", "success"));
        assertEquals("FAILURE", GithubWorkflowJobMapper.normalizeStatus("completed", "timed_out"));
        assertEquals("CANCELLED", GithubWorkflowJobMapper.normalizeStatus("completed", "cancelled"));
        assertEquals("IN_PROGRESS", GithubWorkflowJobMapper.normalizeStatus("in_progress", null));
        assertEquals("QUEUED", GithubWorkflowJobMapper.normalizeStatus("queued", null));
        assertEquals("UNKNOWN", GithubWorkflowJobMapper.normalizeStatus("completed", "mystery"));
    }

    @Test
    void resolveCiUrl_prefers_job_url_then_workflow_url() {
        assertEquals(
                "https://example.test/job",
                GithubWorkflowJobMapper.resolveCiUrl(
                        "https://example.test/job",
                        "https://example.test/run",
                        "owner/repo",
                        "123",
                        "456"
                )
        );
        assertEquals(
                "https://example.test/run",
                GithubWorkflowJobMapper.resolveCiUrl(
                        " ",
                        "https://example.test/run",
                        "owner/repo",
                        "123",
                        "456"
                )
        );
        assertEquals(
                "https://github.com/owner/repo/actions/runs/123/job/456",
                GithubWorkflowJobMapper.resolveCiUrl(
                        null,
                        null,
                        "owner/repo",
                        "123",
                        "456"
                )
        );
        assertEquals(
                "https://github.com/owner/repo/actions/runs/123",
                GithubWorkflowJobMapper.resolveCiUrl(
                        null,
                        null,
                        "owner/repo",
                        "123",
                        null
                )
        );
        assertNull(GithubWorkflowJobMapper.resolveCiUrl(null, null, null, null, null));
    }

    @Test
    void extractTicketKey_finds_first_ticket_like_token() {
        assertEquals("PROJ-123", GithubWorkflowJobMapper.extractTicketKey("feature/PROJ-123-add-ci", "docs", "workflow"));
        assertNull(GithubWorkflowJobMapper.extractTicketKey("feature/no-ticket", "docs"));
    }
}
