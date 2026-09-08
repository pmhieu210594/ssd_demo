package com.sdd.platform.application.usecase.securitydashboard;

import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityScanSnapshot;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Pure, framework-free computation of "Security Finding Resolution Time"
 * for SECURITY-FINDING-RESOLUTION-TIME. See spec-pack.md BR-1 to BR-6
 * (revision 2026-08-26).
 *
 * <p>The result is the duration between the first and the last
 * {@code scan_status = 'FAIL'} scan of the ticket (across every scanner
 * type). There is no cycle concept anymore: a single FAIL record yields a
 * zero duration, and no FAIL record at all yields {@code "-"}.
 */
public final class SecurityFindingResolutionTimeCalculator {

    private SecurityFindingResolutionTimeCalculator() {
    }

    /**
     * @param failHistoryAscByCollectedAt FAIL scan snapshots for one ticket,
     *                                    already sorted ascending by
     *                                    {@code collectedAt}. Caller (adapter)
     *                                    is responsible for the ordering.
     * @return {@code last.collectedAt - first.collectedAt} formatted as
     *         {@code HH:mm:ss} (hours not capped at 24), or {@code "-"} if
     *         there is no FAIL scan at all.
     */
    public static String compute(List<SecurityScanSnapshot> failHistoryAscByCollectedAt) {
        if (failHistoryAscByCollectedAt.isEmpty()) {
            return "-";
        }

        OffsetDateTime first = failHistoryAscByCollectedAt.get(0).collectedAt();
        OffsetDateTime last = failHistoryAscByCollectedAt.get(failHistoryAscByCollectedAt.size() - 1).collectedAt();
        long totalSeconds = Duration.between(first, last).getSeconds();
        return formatDuration(totalSeconds);
    }

    private static String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
