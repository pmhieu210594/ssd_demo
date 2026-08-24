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

    // ── AC-SECFINDRES-2: no scan at all ─────────────────────────────────────

    @Test
    void compute_returnsDash_whenNoHistory() {
        assertEquals("-", SecurityFindingResolutionTimeCalculator.compute(List.of()));
    }

    // ── AC-SECFINDRES-3: open cycle only, never resolved ────────────────────

    @Test
    void compute_returnsDash_whenOnlyOpenCycle() {
        List<SecurityScanSnapshot> history = List.of(
                new SecurityScanSnapshot(2, at("2026-08-01T09:00:00Z")));

        assertEquals("-", SecurityFindingResolutionTimeCalculator.compute(history));
    }

    @Test
    void compute_returnsDash_whenNeverAboveZero() {
        List<SecurityScanSnapshot> history = List.of(
                new SecurityScanSnapshot(0, at("2026-08-01T09:00:00Z")),
                new SecurityScanSnapshot(0, at("2026-08-02T09:00:00Z")));

        assertEquals("-", SecurityFindingResolutionTimeCalculator.compute(history));
    }

    // ── AC-SECFINDRES-1: exactly one closed cycle ───────────────────────────

    @Test
    void compute_sumsSingleClosedCycle() {
        List<SecurityScanSnapshot> history = List.of(
                new SecurityScanSnapshot(3, at("2026-08-01T09:00:00Z")),
                new SecurityScanSnapshot(1, at("2026-08-02T10:00:00Z")),
                new SecurityScanSnapshot(0, at("2026-08-03T12:30:00Z")));

        // 2026-08-01T09:00Z -> 2026-08-03T12:30Z = 2 days 3h30m = 51h30m.
        assertEquals("51:30:00", SecurityFindingResolutionTimeCalculator.compute(history));
    }

    // ── BR-2: non-monotonic unresolvedCount within one cycle ────────────────

    @Test
    void compute_treatsFluctuatingCountAsOneCycle() {
        List<SecurityScanSnapshot> history = List.of(
                new SecurityScanSnapshot(3, at("2026-08-01T00:00:00Z")),
                new SecurityScanSnapshot(1, at("2026-08-01T01:00:00Z")),
                new SecurityScanSnapshot(2, at("2026-08-01T02:00:00Z")),
                new SecurityScanSnapshot(0, at("2026-08-01T05:00:00Z")));

        assertEquals("05:00:00", SecurityFindingResolutionTimeCalculator.compute(history));
    }

    // ── AC-SECFINDRES-7: multiple closed cycles, summed ─────────────────────

    @Test
    void compute_sumsMultipleClosedCycles() {
        List<SecurityScanSnapshot> history = List.of(
                new SecurityScanSnapshot(2, at("2026-08-01T09:00:00Z")),
                new SecurityScanSnapshot(0, at("2026-08-01T15:00:00Z")),
                new SecurityScanSnapshot(1, at("2026-08-05T08:00:00Z")),
                new SecurityScanSnapshot(0, at("2026-08-06T08:00:00Z")));

        assertEquals("30:00:00", SecurityFindingResolutionTimeCalculator.compute(history));
    }

    // ── AC-SECFINDRES-8 / H-SECFINDRES-4: closed cycle(s) + trailing open cycle ──

    @Test
    void compute_ignoresTrailingOpenCycle() {
        List<SecurityScanSnapshot> history = List.of(
                new SecurityScanSnapshot(1, at("2026-08-01T00:00:00Z")),
                new SecurityScanSnapshot(0, at("2026-08-01T02:00:00Z")),
                new SecurityScanSnapshot(3, at("2026-08-02T00:00:00Z")));

        assertEquals("02:00:00", SecurityFindingResolutionTimeCalculator.compute(history));
    }

    // ── AC-SECFINDRES-9: total exceeds 24 hours, HH not capped/reset ────────

    @Test
    void compute_doesNotCapHoursAt24() {
        List<SecurityScanSnapshot> history = List.of(
                new SecurityScanSnapshot(1, at("2026-08-01T00:00:00Z")),
                new SecurityScanSnapshot(0, at("2026-08-03T00:00:00Z")));

        assertEquals("48:00:00", SecurityFindingResolutionTimeCalculator.compute(history));
    }

    // ── ai-review.md F3 (decision: option b, 2026-08-18) ────────────────────
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
                new SecurityScanSnapshot(1, at("2026-08-02T01:01:01Z")),
                new SecurityScanSnapshot(0, at("2026-08-01T00:00:00Z")));

        assertEquals("-25:-1:-1",
                SecurityFindingResolutionTimeCalculator.compute(unsortedHistory));
    }
}
