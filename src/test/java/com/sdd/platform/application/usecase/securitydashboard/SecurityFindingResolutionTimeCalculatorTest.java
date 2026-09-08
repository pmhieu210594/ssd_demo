package com.sdd.platform.application.usecase.securitydashboard;

import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityScanSnapshot;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityFindingResolutionTimeCalculatorTest {

    private static OffsetDateTime at(String isoDateTime) {
        return OffsetDateTime.parse(isoDateTime);
    }

    // ── AC-SECFINDRES-2: no FAIL scan at all ────────────────────────────────

    @Test
    void compute_returnsDash_whenNoHistory() {
        assertEquals("-", SecurityFindingResolutionTimeCalculator.compute(List.of()));
    }

    // ── AC-SECFINDRES-3: exactly one FAIL scan ──────────────────────────────

    @Test
    void compute_returnsZero_whenSingleFailScan() {
        List<SecurityScanSnapshot> history = List.of(
                new SecurityScanSnapshot(at("2026-08-01T09:00:00Z")));

        assertEquals("00:00:00", SecurityFindingResolutionTimeCalculator.compute(history));
    }

    // ── AC-SECFINDRES-1: two FAIL scans ─────────────────────────────────────

    @Test
    void compute_subtractsFirstFromLast_withTwoFailScans() {
        List<SecurityScanSnapshot> history = List.of(
                new SecurityScanSnapshot(at("2026-08-01T09:00:00Z")),
                new SecurityScanSnapshot(at("2026-08-02T10:00:00Z")));

        // 2026-08-01T09:00Z -> 2026-08-02T10:00Z = 1 day 1h = 25h.
        assertEquals("25:00:00", SecurityFindingResolutionTimeCalculator.compute(history));
    }

    // ── BR-2/BR-4: only first and last matter, intermediate FAILs ignored ──

    @Test
    void compute_ignoresIntermediateFailScans() {
        List<SecurityScanSnapshot> history = List.of(
                new SecurityScanSnapshot(at("2026-08-01T00:00:00Z")),
                new SecurityScanSnapshot(at("2026-08-01T01:00:00Z")),
                new SecurityScanSnapshot(at("2026-08-01T02:00:00Z")),
                new SecurityScanSnapshot(at("2026-08-01T05:00:00Z")));

        assertEquals("05:00:00", SecurityFindingResolutionTimeCalculator.compute(history));
    }

    // ── Normal case: FAIL scans spread across multiple days, PASS scans in
    // between are already excluded by the adapter query (BR-1), so the
    // calculator only ever sees FAIL snapshots ──────────────────────────────

    @Test
    void compute_spansAcrossMultipleDays() {
        List<SecurityScanSnapshot> history = List.of(
                new SecurityScanSnapshot(at("2026-08-01T09:00:00Z")),
                new SecurityScanSnapshot(at("2026-08-05T08:00:00Z")),
                new SecurityScanSnapshot(at("2026-08-06T08:00:00Z")));

        // 2026-08-01T09:00Z -> 2026-08-06T08:00Z = 4 days 23h = 119h.
        assertEquals("119:00:00", SecurityFindingResolutionTimeCalculator.compute(history));
    }

    // ── AC-SECFINDRES-9: total exceeds 24 hours, HH not capped/reset ────────

    @Test
    void compute_doesNotCapHoursAt24() {
        List<SecurityScanSnapshot> history = List.of(
                new SecurityScanSnapshot(at("2026-08-01T00:00:00Z")),
                new SecurityScanSnapshot(at("2026-08-03T00:00:00Z")));

        assertEquals("48:00:00", SecurityFindingResolutionTimeCalculator.compute(history));
    }

    // ── H-SECFINDRES-7: caller sorts, calculator trusts the precondition ────
    // compute() does not sort its input and does not validate the
    // "already sorted ascending by collectedAt" precondition documented in
    // its Javadoc. This is a characterization test, not a correctness test:
    // it pins down the current (broken) behavior when a caller violates the
    // precondition, so any future change to this behavior is a deliberate,
    // reviewed decision instead of a silent regression. The contract itself
    // ("caller must sort") is unchanged by this test.

    @Test
    void compute_producesBrokenNegativeDuration_whenInputNotSortedAscending() {
        List<SecurityScanSnapshot> unsortedHistory = List.of(
                new SecurityScanSnapshot(at("2026-08-02T01:01:01Z")),
                new SecurityScanSnapshot(at("2026-08-01T00:00:00Z")));

        assertEquals("-25:-1:-1",
                SecurityFindingResolutionTimeCalculator.compute(unsortedHistory));
    }
}
