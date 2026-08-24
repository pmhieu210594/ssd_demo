package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.governance.FirstCiPassKpiService.FirstCiPassResult;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class FirstCiPassKpiDtos {

    private FirstCiPassKpiDtos() {}

    public record FirstCiPassDto(
            UUID ciRunId,
            UUID pullRequestId,
            String firstRunStatus,
            boolean firstPassSuccess,
            OffsetDateTime firstRunStartedAt
    ) {
        public static FirstCiPassDto from(FirstCiPassResult result) {
            return new FirstCiPassDto(
                    result.ciRunId(),
                    result.pullRequestId(),
                    result.firstRunStatus(),
                    result.firstPassSuccess(),
                    result.firstRunStartedAt()
            );
        }
    }
}
