package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseField;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseMode;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseRequest;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseResult;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseSnapshot;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseStatus;
import com.sdd.platform.application.usecase.docparse.TestArtifactPairViewService.PairView;
import com.sdd.platform.application.usecase.docparse.TestPlanParseService;
import com.sdd.platform.application.usecase.docparse.TestResultsParseService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class TestDocParseDtos {

    private TestDocParseDtos() {}

    public record TestPlanParseRequestDto(
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
                    projectId, repositoryId, ticketId, parseMode, sourcePath, sourceText,
                    parserName == null || parserName.isBlank() ? TestPlanParseService.DEFAULT_PARSER_NAME : parserName,
                    parserVersion == null || parserVersion.isBlank() ? TestPlanParseService.DEFAULT_PARSER_VERSION : parserVersion,
                    traceId, ciGateStatus);
        }
    }

    public record TestResultsParseRequestDto(
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
                    projectId, repositoryId, ticketId, parseMode, sourcePath, sourceText,
                    parserName == null || parserName.isBlank() ? TestResultsParseService.DEFAULT_PARSER_NAME : parserName,
                    parserVersion == null || parserVersion.isBlank() ? TestResultsParseService.DEFAULT_PARSER_VERSION : parserVersion,
                    traceId, ciGateStatus);
        }
    }

    public record TestArtifactParseSnapshotDto(
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
            String schemaVersion,
            Boolean schemaValid,
            Boolean templateEmptyFlag,
            List<String> requiredFieldsMissing,
            String parsedSummaryJson,
            String parserVersion,
            OffsetDateTime collectedAt
    ) {
        public static TestArtifactParseSnapshotDto from(ParseSnapshot snapshot) {
            return new TestArtifactParseSnapshotDto(
                    snapshot.artifactSnapshotId(), snapshot.ticketId(), snapshot.repositoryId(),
                    snapshot.artifactTypeId(), snapshot.phaseId(), snapshot.artifactTypeCode(),
                    snapshot.artifactName(), snapshot.parseMode(), snapshot.parseStatus(),
                    snapshot.sourcePath(), snapshot.contentHash(), snapshot.schemaVersion(),
                    snapshot.schemaValid(), snapshot.templateEmptyFlag(),
                    snapshot.requiredFieldsMissing(), snapshot.parsedSummaryJson(),
                    snapshot.parserVersion(), snapshot.collectedAt());
        }
    }

    public record TestArtifactParseFieldDto(
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
        public static TestArtifactParseFieldDto from(ParseField field) {
            return new TestArtifactParseFieldDto(
                    field.parsedSectionId(), field.artifactSnapshotId(), field.ticketId(),
                    field.sectionType(), field.sectionKey(), field.sectionTextHash(),
                    field.sectionSummary(), field.requiredFlag(), field.presentFlag(),
                    field.validFlag(), field.parseWarning());
        }
    }

    public record TestArtifactParseResultDto(
            TestArtifactParseSnapshotDto snapshot,
            List<TestArtifactParseFieldDto> fields,
            ParseStatus parseStatus,
            List<String> missingFields,
            List<String> warnings,
            String sourceHash
    ) {
        public static TestArtifactParseResultDto from(ParseResult result) {
            return new TestArtifactParseResultDto(
                    result.snapshot() == null ? null : TestArtifactParseSnapshotDto.from(result.snapshot()),
                    result.fields().stream().map(TestArtifactParseFieldDto::from).toList(),
                    result.parseStatus(), result.missingFields(), result.warnings(), result.sourceHash());
        }
    }

    public record TestArtifactPairViewDto(
            UUID ticketId,
            TestArtifactParseSnapshotDto testPlanSnapshot,
            List<TestArtifactParseFieldDto> testPlanFields,
            TestArtifactParseSnapshotDto testResultsSnapshot,
            List<TestArtifactParseFieldDto> testResultsFields,
            boolean pairViewReady
    ) {
        public static TestArtifactPairViewDto from(PairView view) {
            return new TestArtifactPairViewDto(
                    view.ticketId(),
                    view.testPlanSnapshot() == null ? null : TestArtifactParseSnapshotDto.from(view.testPlanSnapshot()),
                    view.testPlanFields().stream().map(TestArtifactParseFieldDto::from).toList(),
                    view.testResultsSnapshot() == null ? null : TestArtifactParseSnapshotDto.from(view.testResultsSnapshot()),
                    view.testResultsFields().stream().map(TestArtifactParseFieldDto::from).toList(),
                    view.pairViewReady());
        }
    }
}
