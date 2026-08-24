package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseField;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseMode;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseRequest;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseResult;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseSnapshot;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ImplPlanParseDtos {

    private ImplPlanParseDtos() {}

    public record ImplPlanParseRequestDto(
            @NotNull UUID projectId,
            @NotNull UUID repositoryId,
            @NotNull UUID ticketId,
            @NotNull ParseMode parseMode,
            @NotBlank String sourcePath,
            String sourceText,
            String parserName,
            String parserVersion,
            String traceId,
            String ciGateStatus
    ) {
        public ParseRequest toRequest() {
            return new ParseRequest(
                    projectId,
                    repositoryId,
                    ticketId,
                    parseMode,
                    sourcePath,
                    sourceText,
                    parserName == null || parserName.isBlank() ? "impl-plan-parser" : parserName,
                    parserVersion == null || parserVersion.isBlank() ? "v1" : parserVersion,
                    traceId,
                    ciGateStatus,
                    null,
                    null
            );
        }
    }

    public record ImplPlanParseSnapshotDto(
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
            OffsetDateTime collectedAt
    ) {
        public static ImplPlanParseSnapshotDto from(ParseSnapshot snapshot) {
            return new ImplPlanParseSnapshotDto(
                    snapshot.artifactSnapshotId(),
                    snapshot.ticketId(),
                    snapshot.repositoryId(),
                    snapshot.artifactTypeId(),
                    snapshot.phaseId(),
                    snapshot.artifactTypeCode(),
                    snapshot.artifactName(),
                    snapshot.parseMode(),
                    snapshot.parseStatus(),
                    snapshot.sourcePath(),
                    snapshot.contentHash(),
                    snapshot.schemaVersion(),
                    snapshot.schemaValid(),
                    snapshot.templateEmptyFlag(),
                    snapshot.requiredFieldsMissing(),
                    snapshot.parsedSummaryJson(),
                    snapshot.parserVersion(),
                    snapshot.collectedAt()
            );
        }
    }

    public record ImplPlanParseFieldDto(
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
    ) {
        public static ImplPlanParseFieldDto from(ParseField field) {
            return new ImplPlanParseFieldDto(
                    field.parsedSectionId(),
                    field.artifactSnapshotId(),
                    field.ticketId(),
                    field.sectionType(),
                    field.sectionKey(),
                    field.sectionTextHash(),
                    field.sectionSummary(),
                    field.requiredFlag(),
                    field.presentFlag(),
                    field.validFlag(),
                    field.parseWarning()
            );
        }
    }

    public record ImplPlanParseResultDto(
            ImplPlanParseSnapshotDto snapshot,
            List<ImplPlanParseFieldDto> fields,
            ParseStatus parseStatus,
            List<String> missingFields,
            List<String> warnings,
            String sourceHash
    ) {
        public static ImplPlanParseResultDto from(ParseResult result) {
            return new ImplPlanParseResultDto(
                    result.snapshot() == null ? null : ImplPlanParseSnapshotDto.from(result.snapshot()),
                    result.fields().stream().map(ImplPlanParseFieldDto::from).toList(),
                    result.parseStatus(),
                    result.missingFields(),
                    result.warnings(),
                    result.sourceHash()
            );
        }
    }
}
