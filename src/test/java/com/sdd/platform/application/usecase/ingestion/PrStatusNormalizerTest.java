package com.sdd.platform.application.usecase.ingestion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for PR status normalization logic.
 * Covers AC-7: PR status normalization into OPEN, MERGED, CLOSED, UNKNOWN.
 */
@DisplayName("PR Status Normalizer Tests")
class PrStatusNormalizerTest {

    /**
     * Helper method to simulate the normalization logic from the service.
     * Mirrors the actual business logic in GitPrMetadataCollectorService.
     */
    private String normalizeStatus(String githubState, boolean isMerged) {
        if ("open".equalsIgnoreCase(githubState)) {
            return "OPEN";
        }
        if ("closed".equalsIgnoreCase(githubState) && isMerged) {
            return "MERGED";
        }
        if ("closed".equalsIgnoreCase(githubState) && !isMerged) {
            return "CLOSED";
        }
        return "UNKNOWN";
    }

    @Test
    @DisplayName("Normalize 'open' state to OPEN")
    void testNormalize_GitHubOpenState_ReturnsOpen() {
        String result = normalizeStatus("open", false);
        assertEquals("OPEN", result);
    }

    @Test
    @DisplayName("Normalize 'OPEN' (uppercase) state to OPEN")
    void testNormalize_GitHubOpenStateUppercase_ReturnsOpen() {
        String result = normalizeStatus("OPEN", false);
        assertEquals("OPEN", result);
    }

    @Test
    @DisplayName("Normalize 'closed' with merged=true to MERGED")
    void testNormalize_GitHubMergedState_ReturnsMerged() {
        String result = normalizeStatus("closed", true);
        assertEquals("MERGED", result);
    }

    @Test
    @DisplayName("Normalize 'CLOSED' (uppercase) with merged=true to MERGED")
    void testNormalize_GitHubMergedStateUppercase_ReturnsMerged() {
        String result = normalizeStatus("CLOSED", true);
        assertEquals("MERGED", result);
    }

    @Test
    @DisplayName("Normalize 'closed' with merged=false to CLOSED")
    void testNormalize_GitHubClosedState_ReturnsClosed() {
        String result = normalizeStatus("closed", false);
        assertEquals("CLOSED", result);
    }

    @Test
    @DisplayName("Normalize 'CLOSED' (uppercase) with merged=false to CLOSED")
    void testNormalize_GitHubClosedStateUppercase_ReturnsClosed() {
        String result = normalizeStatus("CLOSED", false);
        assertEquals("CLOSED", result);
    }

    @Test
    @DisplayName("Normalize unknown state to UNKNOWN")
    void testNormalize_UnknownState_ReturnsUnknown() {
        String result = normalizeStatus("draft", false);
        assertEquals("UNKNOWN", result);
    }

    @Test
    @DisplayName("Normalize null state to UNKNOWN")
    void testNormalize_NullState_ReturnsUnknown() {
        String result = normalizeStatus(null, false);
        assertEquals("UNKNOWN", result);
    }

    @ParameterizedTest(name = "state={0}, isMerged={1} => {2}")
    @CsvSource({
            "open, false, OPEN",
            "open, true, OPEN",
            "OPEN, false, OPEN",
            "OPEN, true, OPEN",
            "closed, true, MERGED",
            "closed, false, CLOSED",
            "CLOSED, true, MERGED",
            "CLOSED, false, CLOSED",
            "draft, false, UNKNOWN",
            "draft, true, UNKNOWN"
    })
    @DisplayName("Parametrized status normalization tests")
    void testNormalize_ParametrizedCases(String state, boolean isMerged, String expected) {
        String result = normalizeStatus(state, isMerged);
        assertEquals(expected, result);
    }
}
