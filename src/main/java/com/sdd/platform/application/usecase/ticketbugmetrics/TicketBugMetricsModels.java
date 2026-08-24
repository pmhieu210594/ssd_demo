package com.sdd.platform.application.usecase.ticketbugmetrics;

import java.util.List;

public final class TicketBugMetricsModels {

    private TicketBugMetricsModels() {
    }

    public record TicketBugMetricsOption(
            String value,
            String label,
            String role
    ) {
    }

    public record TicketBugMetricsOptions(
            List<TicketBugMetricsOption> projects,
            List<TicketBugMetricsOption> repositories,
            List<TicketBugMetricsOption> tickets
    ) {
    }
}
