package com.sdd.platform.application.usecase.scanner;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ArtifactScannerModels {

    private ArtifactScannerModels() {}

    public enum ArtifactScanMode { FULL, TICKET_SCOPED }

    public enum ArtifactScanTriggerType { MANUAL, BATCH, WEBHOOK }

    public enum ArtifactScanStatus { FOUND, MISSING, INACCESSIBLE, ERROR, SKIPPED }

    public record ArtifactScanRequest(
            UUID repositoryId,
            String branchOrRef,
            ArtifactScanMode scanMode,
            List<String> ticketIds,
            ArtifactScanTriggerType triggerType,
            String requestedBy,
            String traceId
    ) {}

    public record RepositoryScope(
            UUID repositoryId,
            UUID projectId,
            String repoNameMasked,
            String defaultBranch
    ) {}

    public record TicketScope(
            UUID ticketId,
            String externalTicketKey
    ) {}

    public record ArtifactTypeScope(
            UUID artifactTypeId,
            UUID phaseId,
            String phaseCode,
            String artifactTypeCode,
            String artifactName,
            String defaultFileName,
            boolean requiredFlag
    ) {}

    public record ConnectorScope(
            UUID connectorId,
            String connectorType,
            String connectorName
    ) {}

    public record ScanRun(
            UUID connectorRunId,
            UUID connectorId,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            String status,
            int recordsRead,
            int recordsWritten,
            String errorMessage,
            String traceId
    ) {}

    public record ArtifactSnapshot(
            UUID artifactSnapshotId,
            UUID connectorRunId,
            UUID repositoryId,
            UUID ticketId,
            String ticketExternalKey,
            String ticketStatus,
            OffsetDateTime ticketLastCommitAt,
            UUID artifactTypeId,
            UUID phaseId,
            String artifactTypeCode,
            String artifactName,
            String defaultFileName,
            boolean requiredFlag,
            String phaseCode,
            String sourcePath,
            boolean existsFlag,
            String contentHash,
            Long sizeBytes,
            OffsetDateTime sourceUpdatedAt,
            boolean templateEmptyFlag,
            boolean needParse,
            String scanStatus,
            String scanMessage,
            OffsetDateTime collectedAt
    ) {}

    public record ParsedSummaryPatch(
            UUID artifactSnapshotId,
            Map<String, Object> parsedSummary,
            List<String> requiredFieldsMissing,
            String parserVersion,
            boolean schemaValid
    ) {}

    public record ParsedAcceptanceCriteria(
            UUID artifactSnapshotId,
            UUID ticketId,
            String acKey,
            String acSummary,
            String acTextHash,
            boolean ambiguousFlag
    ) {}

    public record ParsedSection(
            UUID artifactSnapshotId,
            UUID ticketId,
            String sectionType,
            String sectionKey,
            String sectionSummary,
            String sectionTextHash,
            boolean requiredFlag,
            boolean presentFlag,
            boolean validFlag,
            String parseWarning
    ) {}

    public record ParsedDecision(
            UUID artifactSnapshotId,
            UUID ticketId,
            UUID repositoryId,
            String decisionKey,
            String decisionSummary,
            boolean reasonPresent,
            String impactSummary
    ) {}

    public record ParsedRisk(
            UUID artifactSnapshotId,
            UUID ticketId,
            UUID repositoryId,
            String riskKey,
            String riskSummary,
            String severity,
            boolean mitigationPresent,
            String mitigationSummary
    ) {}

    public record EvidenceEvent(
            UUID artifactSnapshotId,
            UUID ticketId,
            UUID repositoryId,
            UUID connectorRunId,
            String sourceType,
            String eventType,
            String eventResult,
            String eventSummary,
            String eventMetadata
    ) {}

    public record DataQualityRecord(
            UUID connectorRunId,
            UUID repositoryId,
            String sourceType,
            String sourceRef,
            int missingCount,
            int parseErrorCount,
            int schemaViolationCount,
            String errorSummary
    ) {}
}
