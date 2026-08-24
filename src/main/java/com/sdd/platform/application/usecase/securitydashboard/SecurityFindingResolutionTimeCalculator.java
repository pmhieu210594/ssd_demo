package com.sdd.platform.application.usecase.securitydashboard;

import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityScanSnapshot;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Pure, framework-free computation of "Security Finding Resolution Time"
 * for SECURITY-FINDING-RESOLUTION-TIME. See spec-pack.md BR-2 to BR-6.
 *
 * <p>A "cycle" starts at the first snapshot with {@code unresolvedCount > 0}
 * and closes at the next snapshot with {@code unresolvedCount == 0}. The
 * result is the sum of the duration of every closed cycle; an open cycle
 * (no closing snapshot yet) is ignored (H-SECFINDRES-4).
 */
public final class SecurityFindingResolutionTimeCalculator {

    private SecurityFindingResolutionTimeCalculator() {
    }

    /**
     * @param historyAscByCollectedAt SAST scan snapshots for one ticket,
     *                                already sorted ascending by
     *                                {@code collectedAt}. Caller (adapter) is
     *                                responsible for the ordering.
     * @return total resolution time formatted as {@code HH:mm:ss} (hours not
     *         capped at 24), or {@code "-"} if no cycle has closed yet.
     */
    public static String compute(List<SecurityScanSnapshot> historyAscByCollectedAt) {
        long totalSeconds = 0;
        boolean hasClosedCycle = false;
        OffsetDateTime cycleStart = null;

        for (SecurityScanSnapshot snapshot : historyAscByCollectedAt) {
            if (cycleStart == null) {
                if (snapshot.unresolvedCount() > 0) {
                    cycleStart = snapshot.collectedAt();
                }
            } else if (snapshot.unresolvedCount() == 0) {
                totalSeconds += Duration.between(cycleStart, snapshot.collectedAt()).getSeconds();
                hasClosedCycle = true;
                cycleStart = null;
            }
        }

        if (!hasClosedCycle) {
            return "-";
        }
        return formatDuration(totalSeconds);
    }

    private static String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
