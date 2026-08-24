package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.CollectorRunResult;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

public final class GitPrMetadataCollectorDtos {

    private GitPrMetadataCollectorDtos() {
    }

    public record CollectRequest(
            UUID repositoryId,
            @Positive Integer prNumber,
            LocalDate fromDate,
            LocalDate toDate,
            boolean includeClosed
    ) {
    }

    public record CollectResponse(
            UUID runId,
            UUID repositoryId,
            Integer prNumber,
            String status,
            int processedPrCount,
            int processedCommitCount,
            int processedChangedFileCount,
            int failureCount,
            String traceId
    ) {
        public static CollectResponse from(CollectorRunResult result) {
            return new CollectResponse(
                    result.runId(),
                    result.repositoryId(),
                    result.prNumber(),
                    result.status(),
                    result.processedPrCount(),
                    result.processedCommitCount(),
                    result.processedChangedFileCount(),
                    result.failureCount(),
                    result.traceId()
            );
        }
    }
}
