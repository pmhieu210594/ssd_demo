package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.traceability.TraceabilityModels;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class TraceabilityDtos {

    private TraceabilityDtos() {
    }

    public record TraceabilityArtifactDto(
            UUID artifactSnapshotId,
            String artifactTypeCode,
            String artifactName,
            String defaultFileName,
            boolean requiredFlag,
            String sourcePath,
            boolean existsFlag,
            OffsetDateTime collectedAt
    ) {
        public static TraceabilityArtifactDto from(TraceabilityModels.ArtifactCoverage row) {
            return new TraceabilityArtifactDto(
                    row.artifactSnapshotId(),
                    row.artifactTypeCode(),
                    row.artifactName(),
                    row.defaultFileName(),
                    row.requiredFlag(),
                    row.sourcePath(),
                    row.existsFlag(),
                    row.collectedAt()
            );
        }
    }

    public record TraceabilityPullRequestDto(
            UUID prId,
            String externalPrId,
            String externalPrUrl,
            String title,
            String status,
            String sourceBranch,
            String targetBranch,
            OffsetDateTime openedAt,
            OffsetDateTime mergedAt,
            OffsetDateTime closedAt,
            OffsetDateTime collectedAt
    ) {
        public static TraceabilityPullRequestDto from(TraceabilityModels.PullRequestCoverage row) {
            return new TraceabilityPullRequestDto(
                    row.prId(),
                    row.externalPrId(),
                    row.externalPrUrl(),
                    row.title(),
                    row.status(),
                    row.sourceBranch(),
                    row.targetBranch(),
                    row.openedAt(),
                    row.mergedAt(),
                    row.closedAt(),
                    row.collectedAt()
            );
        }
    }

    public record TraceabilityCommitDto(
            UUID commitId,
            String commitHash,
            String branchName,
            String messageHash,
            OffsetDateTime committedAt,
            OffsetDateTime collectedAt
    ) {
        public static TraceabilityCommitDto from(TraceabilityModels.CommitCoverage row) {
            return new TraceabilityCommitDto(
                    row.commitId(),
                    row.commitHash(),
                    row.branchName(),
                    row.messageHash(),
                    row.committedAt(),
                    row.collectedAt()
            );
        }
    }

    public record TraceabilityCiRunDto(
            UUID ciRunId,
            String externalCiRunId,
            String ciUrl,
            String workflowName,
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            OffsetDateTime collectedAt
    ) {
        public static TraceabilityCiRunDto from(TraceabilityModels.CiRunCoverage row) {
            return new TraceabilityCiRunDto(
                    row.ciRunId(),
                    row.externalCiRunId(),
                    row.ciUrl(),
                    row.workflowName(),
                    row.status(),
                    row.startedAt(),
                    row.finishedAt(),
                    row.collectedAt()
            );
        }
    }

    public record TraceabilityLinkDto(
            UUID traceabilityLinkId,
            UUID ticketId,
            String sourceType,
            String sourceId,
            String targetType,
            String targetId,
            double confidence,
            String confidenceLevel,
            String ruleName,
            String evidenceJson,
            OffsetDateTime createdAt
    ) {
        public static TraceabilityLinkDto from(TraceabilityModels.TraceabilityLink row) {
            return new TraceabilityLinkDto(
                    row.traceabilityLinkId(),
                    row.ticketId(),
                    row.sourceType(),
                    row.sourceId(),
                    row.targetType(),
                    row.targetId(),
                    row.confidence(),
                    row.confidenceLevel(),
                    row.ruleName(),
                    row.evidenceJson(),
                    row.createdAt()
            );
        }
    }

    public record TraceabilityBrokenLinkDto(
            String code,
            String item,
            String severity,
            String message
    ) {
        public static TraceabilityBrokenLinkDto from(TraceabilityModels.BrokenLink row) {
            return new TraceabilityBrokenLinkDto(row.code(), row.item(), row.severity(), row.message());
        }
    }

    public record TraceabilityTimelineEventDto(
            UUID eventId,
            String eventType,
            String sourceType,
            String sourceRefId,
            String result,
            String summary,
            OffsetDateTime eventTimestamp
    ) {
        public static TraceabilityTimelineEventDto from(TraceabilityModels.TimelineEvent row) {
            return new TraceabilityTimelineEventDto(
                    row.eventId(),
                    row.eventType(),
                    row.sourceType(),
                    row.sourceRefId(),
                    row.result(),
                    row.summary(),
                    row.eventTimestamp()
            );
        }
    }

    public record TraceabilitySummaryDto(
            UUID ticketId,
            String externalTicketKey,
            String title,
            int completenessPercent,
            int foundCount,
            int expectedCount,
            int artifactCount,
            int prCount,
            int commitCount,
            int ciCount,
            int brokenLinkCount
    ) {
        public static TraceabilitySummaryDto from(TraceabilityModels.Summary summary) {
            return new TraceabilitySummaryDto(
                    summary.ticketId(),
                    summary.externalTicketKey(),
                    summary.title(),
                    summary.completenessPercent(),
                    summary.foundCount(),
                    summary.expectedCount(),
                    summary.artifactCount(),
                    summary.prCount(),
                    summary.commitCount(),
                    summary.ciCount(),
                    summary.brokenLinkCount()
            );
        }
    }

    public record TraceabilityResponseDto(
            TraceabilitySummaryDto summary,
            List<TraceabilityArtifactDto> artifacts,
            List<TraceabilityPullRequestDto> pullRequests,
            List<TraceabilityCommitDto> commits,
            List<TraceabilityCiRunDto> ciRuns,
            List<TraceabilityLinkDto> links,
            List<TraceabilityBrokenLinkDto> brokenLinks,
            List<TraceabilityTimelineEventDto> timelineEvents
    ) {
        public static TraceabilityResponseDto from(TraceabilityModels.TraceabilityView view) {
            return new TraceabilityResponseDto(
                    TraceabilitySummaryDto.from(view.summary()),
                    view.artifacts().stream().map(TraceabilityArtifactDto::from).toList(),
                    view.pullRequests().stream().map(TraceabilityPullRequestDto::from).toList(),
                    view.commits().stream().map(TraceabilityCommitDto::from).toList(),
                    view.ciRuns().stream().map(TraceabilityCiRunDto::from).toList(),
                    view.links().stream().map(TraceabilityLinkDto::from).toList(),
                    view.brokenLinks().stream().map(TraceabilityBrokenLinkDto::from).toList(),
                    view.timelineEvents().stream().map(TraceabilityTimelineEventDto::from).toList()
            );
        }
    }
}
