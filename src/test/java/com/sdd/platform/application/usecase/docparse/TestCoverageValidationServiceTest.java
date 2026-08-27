package com.sdd.platform.application.usecase.docparse;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCoverageValidationServiceTest {

    private final TestCoverageValidationService service = new TestCoverageValidationService();

    @Test
    void validateCoverage_returnsNoWarnings_whenAllSpecAcsAreCovered() {
        List<String> warnings = service.validateCoverage(
                List.of("AC-1", "AC-2"),
                List.of("AC-1", "AC-2"));

        assertTrue(warnings.isEmpty());
    }

    @Test
    void validateCoverage_reportsMissingAndUnknownReferences_inStableOrder() {
        List<String> warnings = service.validateCoverage(
                List.of("AC-1", "AC-2"),
                List.of("AC-1", "AC-99"));

        assertEquals(List.of("AC_NOT_COVERED:AC-2", "UNKNOWN_AC_REFERENCE:AC-99"), warnings);
    }

    @Test
    void validateCoverage_reportsAllMissing_whenMatrixIsEmpty() {
        List<String> warnings = service.validateCoverage(
                List.of("AC-1", "AC-2", "AC-3"),
                List.of());

        assertEquals(List.of(
                "AC_NOT_COVERED:AC-1",
                "AC_NOT_COVERED:AC-2",
                "AC_NOT_COVERED:AC-3"), warnings);
    }
}
