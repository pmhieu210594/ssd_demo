package com.sdd.platform.application.usecase.docparse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DocParseModels {

    private DocParseModels() {}

    public enum ParseMode { DRAFT, OFFICIAL }

    public enum ParseStatus { SUCCESS, PARTIAL, NOT_FOUND, PARSE_ERROR }

    public record ParseRequest(
            UUID projectId,
            UUID repositoryId,
            UUID ticketId,
            ParseMode parseMode,
            String sourcePath,
            String sourceText,
            String parserName,
            String parserVersion,
            String traceId,
            String ciGateStatus,
            UUID connectorRunId,
            OffsetDateTime sourceUpdatedAt
    ) {}

    public record ParseSnapshot(
            UUID artifactSnapshotId,
            UUID ticketId,
            UUID repositoryId,
            UUID artifactTypeId,
            UUID phaseId,
            String artifactTypeCode,
            String artifactName,
            String parseMode,
            String parseStatus,
            String sourcePath,
            String contentHash,
            Integer schemaVersion,
            Boolean schemaValid,
            Boolean templateEmptyFlag,
            List<String> requiredFieldsMissing,
            String parsedSummaryJson,
            String parserVersion,
            UUID connectorRunId,
            OffsetDateTime sourceUpdatedAt,
            OffsetDateTime collectedAt
    ) {}

    public record ParseField(
            UUID parsedSectionId,
            UUID artifactSnapshotId,
            UUID ticketId,
            String sectionType,
            String sectionKey,
            String sectionTextHash,
            String sectionSummary,
            boolean requiredFlag,
            boolean presentFlag,
            Boolean validFlag,
            String parseWarning
    ) {}

    public record FieldSpec(
            String fieldKey,
            String sectionKey,
            String fieldLabel,
            String sourceSection,
            String purpose,
            boolean required,
            int displayOrder
    ) {}

    public record FieldResult(
            FieldSpec spec,
            boolean present,
            String valueText,
            String valueJson,
            String extractionStatus,
            String warning
    ) {}

    public record ParseResult(
            ParseSnapshot snapshot,
            List<ParseField> fields,
            ParseStatus parseStatus,
            List<String> missingFields,
            List<String> warnings,
            String sourceHash,
            Map<String, String> fieldValues
    ) {}

    public record ParseEvidenceEvent(
            UUID ticketId,
            UUID repositoryId,
            UUID artifactSnapshotId,
            String eventType,
            String result,
            String summary,
            String metadataJson,
            OffsetDateTime eventTimestamp,
            String sourceType,
            String sourceRefId
    ) {}

    public record ParseDataQuality(
            UUID projectId,
            UUID repositoryId,
            UUID connectorRunId,
            String sourceType,
            String sourceRef,
            int missingCount,
            int parseErrorCount,
            int schemaViolationCount,
            Integer freshnessDelayMinutes,
            String errorSummary,
            OffsetDateTime checkedAt
    ) {}
}
