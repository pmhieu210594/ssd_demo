package com.sdd.platform.application.usecase.ingestion;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class CiRunModels {

    private CiRunModels() {}

    public enum SaveOutcome {
        INSERTED,
        UPDATED
    }

    public record RepositoryScope(
            UUID repositoryId,
            UUID projectId,
            String repositoryNameMasked,
            String defaultBranch
    ) {}

    public record PullRequestScope(
            UUID pullRequestId,
            UUID ticketId,
            Integer externalPrNumber,
            String sourceBranch
    ) {}

    public record ConnectorScope(
            UUID connectorId,
            String connectorType,
            String connectorName
    ) {}

    public record CiRunMetadataView(
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
    ) {}

    public record CollectorResult(String handled, int recordsAffected) {}

    public record JobPayload(
            String repositoryFullName,
            String workflowRunId,
            String workflowName,
            String workflowRunUrl,
            String externalJobId,
            String jobName,
            String jobStatus,
            String jobConclusion,
            String jobUrl,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            Integer pullRequestNumber,
            String branchName
    ) {}
}
