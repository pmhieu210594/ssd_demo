package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.governance.ExceptionKpiService.ExceptionKpiResult;
import com.sdd.platform.application.usecase.governance.ExceptionKpiService.ExceptionSummary;

import java.util.List;
import java.util.UUID;

public final class ExceptionKpiDtos {

    private ExceptionKpiDtos() {}

    public record ExceptionSummaryDto(
            UUID exceptionId,
            String exceptionType,
            boolean reasonPresent,
            boolean approved,
            String followUpStatus,
            String sourceSection,
            String linkedReportPath
    ) {
        public static ExceptionSummaryDto from(ExceptionSummary summary) {
            return new ExceptionSummaryDto(
                    summary.exceptionId(),
                    summary.exceptionType(),
                    summary.reasonPresent(),
                    summary.approved(),
                    summary.followUpStatus(),
                    summary.sourceSection(),
                    summary.linkedReportPath()
            );
        }
    }

    public record ExceptionKpiDto(
            UUID ticketId,
            int totalExceptions,
            int openExceptions,
            int approvedExceptions,
            boolean hasExplicitExceptions,
            List<ExceptionSummaryDto> exceptions
    ) {
        public static ExceptionKpiDto from(ExceptionKpiResult result) {
            List<ExceptionSummaryDto> summaries = result.exceptions().stream()
                    .map(ExceptionSummaryDto::from)
                    .toList();
            return new ExceptionKpiDto(
                    result.ticketId(),
                    result.totalExceptions(),
                    result.openExceptions(),
                    result.approvedExceptions(),
                    result.hasExplicitExceptions(),
                    summaries
            );
        }
    }
}
