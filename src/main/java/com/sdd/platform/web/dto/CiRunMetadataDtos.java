package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.ingestion.CiRunModels.CiRunMetadataView;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class CiRunMetadataDtos {

    private CiRunMetadataDtos() {}

    public record CiRunMetadataDto(
            UUID ciRunId,
            UUID repositoryId,
            String repositoryNameMasked,
            UUID projectId,
            String ciProvider,
            String workflowName,
            String jobName,
            String externalRunId,
            String externalJobId,
            String ciUrl,
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            OffsetDateTime collectedAt
    ) {
        public static CiRunMetadataDto from(CiRunMetadataView view) {
            return new CiRunMetadataDto(
                    view.ciRunId(),
                    view.repositoryId(),
                    view.repositoryNameMasked(),
                    view.projectId(),
                    view.ciProvider(),
                    view.workflowName(),
                    view.jobName(),
                    view.externalRunId(),
                    view.externalJobId(),
                    view.ciUrl(),
                    view.status(),
                    view.startedAt(),
                    view.completedAt(),
                    view.collectedAt()
            );
        }
    }
}
