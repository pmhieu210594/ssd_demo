package com.sdd.platform.application.usecase.docparse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.persistence.CiRunRepositoryPort;
import com.sdd.platform.application.port.out.persistence.AcCoveragePort;
import com.sdd.platform.application.port.out.persistence.DocParsePersistencePort;
import com.sdd.platform.application.port.out.persistence.TestEvidencePersistencePort;
import com.sdd.platform.application.usecase.docparse.DocParseModels.FieldSpec;
import com.sdd.platform.application.usecase.ingestion.CiRunModels.CiRunMetadataView;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseDataQuality;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseEvidenceEvent;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseField;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseMode;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseRequest;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseResult;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseSnapshot;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseStatus;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreService;
import com.sdd.platform.domain.service.ArtifactNormalizer;
import com.sdd.platform.domain.service.ArtifactNormalizer.ParsedArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TestPlanParseService {

    private static final Logger log = LoggerFactory.getLogger(TestPlanParseService.class);

    public static final String DOCUMENT_TYPE = "TEST_PLAN";
    public static final String DEFAULT_PARSER_NAME = "test-plan-parser";
    public static final String DEFAULT_PARSER_VERSION = "v1";
    public static final String SCHEMA_VERSION = "TEST_PLAN_V1";
    public static final String SECTION_TYPE = "test-plan";

    private static final List<FieldSpec> FIELD_SPECS = List.of(
            new FieldSpec("purpose", "purpose", "Purpose",
                    "1. Purpose", "Test plan purpose", true, 1),
            new FieldSpec("ac_matrix_test_type", "ac-matrix-test-type", "AC Matrix / Test Type",
                    "2. AC Matrix ↔ Test Type", "AC to test type mapping", true, 2),
            new FieldSpec("priority", "priority", "Priority",
                    "3. Priority", "Test item priorities", true, 3),
            new FieldSpec("reuse_existing_test", "reuse-existing-test", "Reuse Existing Test",
                    "4. Reuse Existing Test", "Reusable existing tests", false, 4),
            new FieldSpec("additional_test_this_time", "additional-test-this-time", "Additional Test This Time",
                    "5. Additional Test This Time", "New tests added for this ticket", true, 5),
            new FieldSpec("e2e_step_by_step_scenarios", "e2e-step-by-step-scenarios", "E2E Step-by-step Scenarios",
                    "E2E Step-by-step Scenarios", "End-to-end scenario steps", true, 6),
            new FieldSpec("areas_intentionally_left_untested_this_time", "areas-intentionally-left-untested-this-time",
                    "Areas Intentionally Left Untested This Time",
                    "6. Areas intentionally left untested this time", "Untested areas and justification", false, 7),
            new FieldSpec("data_testing_principles", "data-testing-principles", "Data Testing Principles",
                    "7. Data testing principles", "Data usage rules for tests", true, 8),
            new FieldSpec("execution_command", "execution-command", "Execution Command",
                    "8. Execution command", "Commands to run the tests", true, 9),
            new FieldSpec("stop_condition", "stop-condition", "Stop Condition",
                    "9. Stop Condition", "Conditions that halt testing", true, 10),
            new FieldSpec("required_human_decision", "required-human-decision", "Required Human Decision",
                    "10. Required Human Decision", "Decisions needing human confirmation", false, 11));

    private final ArtifactNormalizer artifactNormalizer;
    private final DocParsePersistencePort persistence;
    private final ObjectMapper objectMapper;
    private final TestCoverageValidationService coverageValidationService;
    private final AcCoveragePort acCoveragePort;
    private final TestEvidencePersistencePort testEvidencePersistencePort;
    private final CiRunRepositoryPort ciRunRepositoryPort;
    private final EvidenceQualityScoreService evidenceQualityScoreService;

    @Autowired
    public TestPlanParseService(
            ArtifactNormalizer artifactNormalizer,
            @Qualifier("testPlanDocParse") DocParsePersistencePort persistence,
            ObjectMapper objectMapper,
            TestCoverageValidationService coverageValidationService,
            AcCoveragePort acCoveragePort,
            TestEvidencePersistencePort testEvidencePersistencePort,
            CiRunRepositoryPort ciRunRepositoryPort,
            EvidenceQualityScoreService evidenceQualityScoreService) {
        this.artifactNormalizer = artifactNormalizer;
        this.persistence = persistence;
        this.objectMapper = objectMapper;
        this.coverageValidationService = coverageValidationService;
        this.acCoveragePort = acCoveragePort;
        this.testEvidencePersistencePort = testEvidencePersistencePort;
        this.ciRunRepositoryPort = ciRunRepositoryPort;
        this.evidenceQualityScoreService = evidenceQualityScoreService;
    }

    public TestPlanParseService(
            ArtifactNormalizer artifactNormalizer,
            @Qualifier("testPlanDocParse") DocParsePersistencePort persistence,
            ObjectMapper objectMapper,
            TestCoverageValidationService coverageValidationService,
            AcCoveragePort acCoveragePort,
            CiRunRepositoryPort ciRunRepositoryPort) {
        this(artifactNormalizer, persistence, objectMapper, coverageValidationService, acCoveragePort, null, ciRunRepositoryPort, null);
    }

    public TestPlanParseService(
            ArtifactNormalizer artifactNormalizer,
            @Qualifier("testPlanDocParse") DocParsePersistencePort persistence,
            ObjectMapper objectMapper,
            TestCoverageValidationService coverageValidationService,
            AcCoveragePort acCoveragePort,
            TestEvidencePersistencePort testEvidencePersistencePort,
            CiRunRepositoryPort ciRunRepositoryPort) {
        this(artifactNormalizer, persistence, objectMapper, coverageValidationService, acCoveragePort,
                testEvidencePersistencePort, ciRunRepositoryPort, null);
    }

    private static final List<Pattern> PLACEHOLDER_PATTERNS = List.of(
            Pattern.compile("<TBD>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("TODO", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\{\\{.*?}}"),
            Pattern.compile("TO_BE_DEFINED", Pattern.CASE_INSENSITIVE));

    @Transactional
    public ParseResult parseAndStore(ParseRequest request) {
        log.info("TestPlan parse start repoId={} ticketId={} mode={} path={} traceId={}",
                request.repositoryId(), request.ticketId(), request.parseMode(),
                request.sourcePath(), request.traceId());
        ParseResult parsed = parse(request);
        List<String> specPackAcKeys = acCoveragePort.findActiveAcKeys(request.ticketId());
        if (!specPackAcKeys.isEmpty()) {
            List<String> matrixAcIds = extractAcIds(parsed.fieldValues().get("ac_matrix_test_type"));
            List<String> coverageWarnings = coverageValidationService.validateCoverage(specPackAcKeys, matrixAcIds);
            if (!coverageWarnings.isEmpty()) {
                List<String> mergedWarnings = new ArrayList<>(parsed.warnings());
                mergedWarnings.addAll(coverageWarnings);
                ParseStatus newStatus = parsed.parseStatus() == ParseStatus.SUCCESS
                        ? ParseStatus.PARTIAL : parsed.parseStatus();
                parsed = new ParseResult(null, parsed.fields(), newStatus, parsed.missingFields(),
                        mergedWarnings, parsed.sourceHash(), parsed.fieldValues());
            }
        }
        ParseSnapshot snapshot = persistence.upsertSnapshot(buildSnapshot(request, parsed));
        List<ParseField> sections = buildSections(snapshot.artifactSnapshotId(), request.ticketId(), parsed);
        persistence.replaceSections(snapshot.artifactSnapshotId(), request.ticketId(), sections);
        persistPlannedCoverage(snapshot.artifactSnapshotId(), request.ticketId(), parsed);
        persistence.persistEvidenceEvent(buildEvidenceEvent(request, snapshot, parsed));
        ParseDataQuality quality = buildDataQuality(request, snapshot, parsed);
        if (quality.missingCount() > 0 || quality.parseErrorCount() > 0 || quality.schemaViolationCount() > 0) {
            persistence.persistDataQuality(quality);
        }
        if (evidenceQualityScoreService != null && request.ticketId() != null) {
            evidenceQualityScoreService.recalculateFromParser(request.ticketId(), null, request.traceId());
        }
        log.info("TestPlan parse stored repoId={} ticketId={} path={} status={} snapshotId={} sections={}",
                request.repositoryId(), request.ticketId(), request.sourcePath(),
                parsed.parseStatus(), snapshot.artifactSnapshotId(), sections.size());
        return new ParseResult(snapshot, sections, parsed.parseStatus(), parsed.missingFields(),
                parsed.warnings(), parsed.sourceHash(), parsed.fieldValues());
    }

    @Transactional(readOnly = true)
    public Optional<ParseSnapshot> latestSnapshot(UUID ticketId, ParseMode parseMode) {
        return persistence.findLatestSnapshot(ticketId, DOCUMENT_TYPE, parseMode);
    }

    @Transactional(readOnly = true)
    public Optional<ParseSnapshot> snapshot(UUID snapshotId) {
        return persistence.findSnapshotById(snapshotId);
    }

    @Transactional(readOnly = true)
    public Optional<ParseResult> detail(UUID snapshotId) {
        return persistence.findSnapshotById(snapshotId).map(snapshot -> {
            List<ParseField> sections = persistence.findSections(snapshotId);
            Map<String, String> fieldValues = new LinkedHashMap<>();
            List<String> missing = new ArrayList<>();
            for (ParseField section : sections) {
                fieldValues.put(section.sectionKey(), section.sectionSummary());
                if (!section.presentFlag())
                    missing.add(section.sectionKey());
            }
            List<String> warnings = sections.stream()
                    .map(ParseField::parseWarning)
                    .filter(w -> w != null && !w.isBlank())
                    .distinct().toList();
            ParseStatus status = parseStatus(snapshot.parseStatus());
            return new ParseResult(snapshot, sections, status, missing, warnings, snapshot.contentHash(), fieldValues);
        });
    }

    @Transactional(readOnly = true)
    public List<ParseField> fields(UUID snapshotId) {
        return persistence.findSections(snapshotId);
    }

    @Transactional(readOnly = true)
    public List<ParseSnapshot> recentSnapshots(UUID ticketId, ParseMode parseMode, int limit) {
        return persistence.findSnapshots(ticketId, DOCUMENT_TYPE, parseMode, limit);
    }

    private ParseResult parse(ParseRequest request) {
        if (request.sourceText() == null) {
            return emptyResult(ParseStatus.NOT_FOUND, null,
                    List.of("source_text_missing"), List.of("source_text_missing"));
        }
        if (request.sourceText().isBlank()) {
            return emptyResult(ParseStatus.PARSE_ERROR, sha256(request.sourceText()),
                    List.of("source_text_empty"), List.of("source_text_empty"));
        }

        ParsedArtifact parsedArtifact = artifactNormalizer.parse(request.sourceText());
        Map<String, String> sections = parsedArtifact.sections();
        Map<String, String> fieldValues = new LinkedHashMap<>();
        List<String> missingFields = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Integer> headingCounts = countHeadingOccurrences(request.sourceText());
        warnings.addAll(
                validateStructure(request.sourceText()));
        for (FieldSpec spec : FIELD_SPECS) {
            String value = sections.get(spec.sectionKey());

            boolean present = value != null
                    && !value.isBlank();

            boolean placeholder = containsPlaceholder(value);

            if (placeholder) {
                warnings.add(spec.fieldKey() + ":placeholder");
            }

            int count = headingCounts.getOrDefault(spec.sectionKey(), 0);

            if (!present) {
                missingFields.add(spec.fieldKey());

                if (spec.required()) {
                    warnings.add(spec.fieldKey() + ":missing");
                }
            } else if (count > 1) {
                warnings.add(spec.fieldKey() + ":duplicate");
            }

            fieldValues.put(spec.fieldKey(), value);
        }

        ParseStatus status;
        boolean hasWarnings = warnings.stream().anyMatch(w -> w.contains("duplicate")
                || w.contains("placeholder"));

        if (missingFields.size() == FIELD_SPECS.size()) {
            status = ParseStatus.NOT_FOUND;
        } else if (!missingFields.isEmpty() || hasWarnings) {
            status = ParseStatus.PARTIAL;
        } else {
            status = ParseStatus.SUCCESS;
        }

        List<String> summaryWarnings = new ArrayList<>(warnings);
        if (status == ParseStatus.PARTIAL && summaryWarnings.isEmpty()) {
            summaryWarnings.add("partial_parse");
        }

        List<ParseField> fields = buildSections(null, request.ticketId(), fieldValues, warnings, status);
        return new ParseResult(null, fields, status, missingFields, summaryWarnings,
                parsedArtifact.contentHash(), fieldValues);
    }

    private ParseSnapshot buildSnapshot(ParseRequest request, ParseResult parsed) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new ParseSnapshot(null, request.ticketId(), request.repositoryId(),
                null, null, DOCUMENT_TYPE, "Test Plan",
                request.parseMode().name(), parsed.parseStatus().name(),
                request.sourcePath(), parsed.sourceHash(), SCHEMA_VERSION,
                parsed.parseStatus() == ParseStatus.SUCCESS,
                request.sourceText() != null && request.sourceText().isBlank(),
                parsed.missingFields(), buildParsedSummaryJson(request, parsed),
                request.parserVersion(), now);
    }

    private List<ParseField> buildSections(UUID snapshotId, UUID ticketId, ParseResult parsed) {
        return buildSections(snapshotId, ticketId, parsed.fieldValues(), parsed.warnings(), parsed.parseStatus());
    }

    private List<ParseField> buildSections(UUID snapshotId, UUID ticketId,
            Map<String, String> fieldValues,
            List<String> warnings, ParseStatus status) {
        List<ParseField> sections = new ArrayList<>();
        for (FieldSpec spec : FIELD_SPECS) {
            String value = fieldValues.get(spec.fieldKey());
            boolean present = value != null && !value.isBlank();
            boolean duplicate = warnings.stream().anyMatch(w -> w.startsWith(spec.fieldKey() + ":duplicate"));
            boolean placeholder = warnings.stream()
                    .anyMatch(w -> w.startsWith(spec.fieldKey() + ":placeholder"));
            boolean invalidStructure = warnings.stream()
                    .anyMatch(w -> w.startsWith(spec.fieldKey() + ":invalid_order"));
            String warning = null;
            String extractionStatus;
            if (!present) {
                extractionStatus = "MISSING";
                warning = "REQUIRED_SECTION_MISSING";
            } else if (duplicate) {
                extractionStatus = "PARTIAL";
                warning = "DUPLICATE_HEADING";
            } else if (placeholder) {
                extractionStatus = "PARTIAL";
                warning = "PLACEHOLDER_DETECTED";
            } else if (invalidStructure) {
                extractionStatus = "PARTIAL";
                warning = "INVALID_STRUCTURE";
            } else {
                extractionStatus = "FOUND";
            }
            sections.add(new ParseField(null, snapshotId, ticketId, SECTION_TYPE, spec.fieldKey(),
                    value == null ? null : sha256(value), value,
                    spec.required(), present, "FOUND".equals(extractionStatus), warning));
        }
        sections.sort(Comparator.comparingInt(f -> fieldOrder(f.sectionKey())));
        return sections;
    }

    private int fieldOrder(String fieldKey) {
        for (FieldSpec spec : FIELD_SPECS) {
            if (spec.fieldKey().equals(fieldKey))
                return spec.displayOrder();
        }
        return Integer.MAX_VALUE;
    }

    private ParseResult emptyResult(ParseStatus status, String sourceHash,
            List<String> missingFields, List<String> warnings) {
        Map<String, String> fieldValues = new LinkedHashMap<>();
        for (FieldSpec spec : FIELD_SPECS)
            fieldValues.put(spec.fieldKey(), null);
        List<ParseField> fields = buildSections(null, null, fieldValues, List.of(), status);
        return new ParseResult(null, fields, status, missingFields, warnings, sourceHash, fieldValues);
    }

    private ParseEvidenceEvent buildEvidenceEvent(ParseRequest request, ParseSnapshot snapshot, ParseResult parsed) {
        boolean failed = parsed.parseStatus() == ParseStatus.PARSE_ERROR || parsed.parseStatus() == ParseStatus.NOT_FOUND;
        String eventType = failed ? "PARSE_FAILED" : "PARSE_COMPLETED";
        String result;
        if (parsed.parseStatus() == ParseStatus.SUCCESS) {
            result = "SUCCESS";
        } else if (parsed.parseStatus() == ParseStatus.PARTIAL) {
            result = "PARTIAL";
        } else {
            result = "FAILED";
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("parseStatus", parsed.parseStatus().name());
        meta.put("warningCount", parsed.warnings().size());
        meta.put("missingCount", parsed.missingFields().size());
        meta.put("fieldCount", (int) parsed.fieldValues().values().stream().filter(v -> v != null && !v.isBlank()).count());
        meta.put("traceId", request.traceId());
        appendCiRunMetadata(meta, resolveCiRunMetadata(request));
        return new ParseEvidenceEvent(
                request.ticketId(),
                request.repositoryId(),
                snapshot.artifactSnapshotId(),
                eventType,
                result,
                DOCUMENT_TYPE + " parsed: " + parsed.parseStatus().name(),
                toJson(meta),
                OffsetDateTime.now(ZoneOffset.UTC),
                DOCUMENT_TYPE + "_PARSE",
                snapshot.artifactSnapshotId().toString()
        );
    }

    private ParseDataQuality buildDataQuality(ParseRequest request, ParseSnapshot snapshot, ParseResult parsed) {
        int missingCount = (int) parsed.missingFields().stream()
                .filter(key -> FIELD_SPECS.stream().filter(FieldSpec::required).anyMatch(s -> s.fieldKey().equals(key)))
                .count();
        int parseErrorCount = parsed.parseStatus() == ParseStatus.PARSE_ERROR ? 1 : 0;
        List<String> coverageViolations = parsed.warnings().stream()
                .filter(w -> w.startsWith("AC_NOT_COVERED:") || w.startsWith("UNKNOWN_AC_REFERENCE:"))
                .toList();
        int schemaViolationCount = (int) parsed.warnings().stream()
                .filter(w -> w.contains(":duplicate") || w.contains(":invalid_order"))
                .count() + coverageViolations.size();
        List<String> errorParts = new ArrayList<>();
        if (parseErrorCount > 0) errorParts.add("parse_error");
        errorParts.addAll(parsed.missingFields());
        errorParts.addAll(coverageViolations);
        String errorSummary = errorParts.isEmpty() ? null : String.join("; ", errorParts);
        return new ParseDataQuality(
                request.projectId(),
                request.repositoryId(),
                DOCUMENT_TYPE + "_PARSE",
                snapshot.sourcePath(),
                missingCount,
                parseErrorCount,
                schemaViolationCount,
                errorSummary,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String buildParsedSummaryJson(ParseRequest request, ParseResult parsed) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("documentType", DOCUMENT_TYPE);
        summary.put("parseMode", request.parseMode().name());
        summary.put("parseStatus", parsed.parseStatus().name());
        summary.put("sourceHash", parsed.sourceHash());
        summary.put("parserName", request.parserName());
        summary.put("parserVersion", request.parserVersion());
        summary.put("traceId", request.traceId());
        summary.put("ciGateStatus", request.ciGateStatus());
        appendCiRunMetadata(summary, resolveCiRunMetadata(request));
        summary.put("parsedFieldCount",
                parsed.fieldValues().values().stream().filter(v -> v != null && !v.isBlank()).count());
        summary.put("missingFieldCount", parsed.missingFields().size());
        summary.put("missingFields", parsed.missingFields());
        summary.put("warnings", parsed.warnings());
        summary.put("fieldValues", parsed.fieldValues());
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Optional<CiRunMetadataView> resolveCiRunMetadata(ParseRequest request) {
        if (request == null || request.repositoryId() == null || request.ticketId() == null) {
            return Optional.empty();
        }
        return ciRunRepositoryPort.findLatestCiRunByRepositoryAndTicket(request.repositoryId(), request.ticketId());
    }

    private void appendCiRunMetadata(Map<String, Object> target, Optional<CiRunMetadataView> ciRun) {
        if (target == null || ciRun == null || ciRun.isEmpty()) {
            return;
        }
        CiRunMetadataView view = ciRun.get();
        target.put("ciRunId", view.ciRunId());
        target.put("ciProvider", view.ciProvider());
        target.put("workflowName", view.workflowName());
        target.put("jobName", view.jobName());
        target.put("externalRunId", view.externalRunId());
        target.put("externalJobId", view.externalJobId());
        target.put("ciUrl", view.ciUrl());
        target.put("ciStatus", view.status());
        target.put("ciCollectedAt", view.collectedAt());
    }

    private ParseStatus parseStatus(String value) {
        if (value == null || value.isBlank())
            return ParseStatus.PARSE_ERROR;
        try {
            return ParseStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return ParseStatus.PARSE_ERROR;
        }
    }

    private Map<String, Integer> countHeadingOccurrences(String content) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (content == null || content.isBlank())
            return counts;
        for (String line : content.split("\\R", -1)) {
            if (line.startsWith("## ") || line.startsWith("### ")) {
                String heading = line.replaceFirst("^#+\\s+", "");
                String canonical = canonicalizeHeading(heading);
                if (!canonical.isBlank())
                    counts.merge(canonical, 1, Integer::sum);
            }
        }
        return counts;
    }

    private String canonicalizeHeading(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT).trim();
        normalized = normalized.replaceFirst("^\\d+[\\p{Punct}\\s]*", "");
        normalized = normalized.replaceAll("[／/]", "-");
        normalized = normalized.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-");
        normalized = normalized.replaceAll("-+", "-");
        return normalized.replaceAll("^-|-$", "");
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean containsPlaceholder(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        return PLACEHOLDER_PATTERNS.stream()
                .anyMatch(p -> p.matcher(value).find());
    }

    private List<String> validateStructure(String sourceText) {

        List<String> warnings = new ArrayList<>();

        int previousIndex = -1;

        for (FieldSpec spec : FIELD_SPECS) {

            int idx = sourceText.indexOf(spec.sourceSection());

            if (idx < 0) {
                continue;
            }

            if (previousIndex > idx) {
                warnings.add(spec.fieldKey() + ":invalid_order");
            }

            previousIndex = idx;
        }

        return warnings;
    }

    private List<String> extractAcIds(String text) {

        if (text == null || text.isBlank()) {
            return List.of();
        }

        Pattern pattern = Pattern.compile("\\bAC-[A-Za-z0-9_-]+\\b");

        Matcher matcher = pattern.matcher(text);

        List<String> ids = new ArrayList<>();

        while (matcher.find()) {
            ids.add(matcher.group());
        }

        return ids.stream()
                .distinct()
                .toList();
    }

    private void persistPlannedCoverage(UUID snapshotId, UUID ticketId, ParseResult parsed) {
        if (testEvidencePersistencePort == null || snapshotId == null || ticketId == null || parsed == null) {
            return;
        }

        List<String> acKeys = extractCoveredAcIdsFromMatrix(parsed.fieldValues().get("ac_matrix_test_type"));
        testEvidencePersistencePort.replacePlannedCoverage(snapshotId, ticketId, parsed.sourceHash(), acKeys);

        ParsedTestCases parsedTestCases = extractPlannedTestCases(
                snapshotId, ticketId, parsed.fieldValues().get("additional_test_this_time"));
        if (!parsedTestCases.testCases().isEmpty()) {
            testEvidencePersistencePort.upsertPlannedTestCases(parsedTestCases.testCases());
        }
        if (!parsedTestCases.acMappings().isEmpty()) {
            testEvidencePersistencePort.upsertTestCaseAcMappings(parsedTestCases.acMappings());
            testEvidencePersistencePort.linkTestCasesToPlannedCoverage(ticketId);
        }
    }

    private record ParsedTestCases(
            List<TestEvidencePersistencePort.PlannedTestCaseRecord> testCases,
            List<TestEvidencePersistencePort.TestCaseAcRecord> acMappings) {
    }

    private ParsedTestCases extractPlannedTestCases(UUID snapshotId, UUID ticketId, String sectionText) {
        if (sectionText == null || sectionText.isBlank()) {
            return new ParsedTestCases(List.of(), List.of());
        }

        List<TestEvidencePersistencePort.PlannedTestCaseRecord> testCases = new ArrayList<>();
        List<TestEvidencePersistencePort.TestCaseAcRecord> acMappings = new ArrayList<>();

        for (String rawLine : sectionText.split("\\R")) {
            String line = rawLine.trim();
            if (!line.startsWith("|") || line.chars().filter(ch -> ch == '|').count() < 2) {
                continue;
            }
            List<String> cells = splitTableRow(line);
            if (cells.size() < 2) {
                continue;
            }
            String tcId = cells.get(0).trim();
            if (tcId.isBlank() || isTableHeaderCell(tcId) || isSeparatorCell(tcId)) {
                continue;
            }

            String testCaseName = cells.size() > 1 ? cells.get(1).trim() : null;

            String basis = String.join("|", safe(ticketId), safe(tcId));
            UUID testCaseId = UUID.nameUUIDFromBytes(basis.getBytes(StandardCharsets.UTF_8));

            testCases.add(new TestEvidencePersistencePort.PlannedTestCaseRecord(
                    snapshotId, ticketId, tcId, testCaseName, null));

            // last column is "related AC" — may contain comma-separated values
            String relatedAcCell = cells.get(cells.size() - 1).trim();
            if (!relatedAcCell.isBlank() && !isTableHeaderCell(relatedAcCell) && !isSeparatorCell(relatedAcCell)) {
                for (String acKey : relatedAcCell.split(",")) {
                    String trimmed = acKey.trim();
                    if (!trimmed.isBlank()) {
                        acMappings.add(new TestEvidencePersistencePort.TestCaseAcRecord(
                                testCaseId, ticketId, trimmed));
                    }
                }
            }
        }

        return new ParsedTestCases(testCases, acMappings);
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<String> extractCoveredAcIdsFromMatrix(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> acKeys = new ArrayList<>();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (!line.startsWith("|") || line.chars().filter(ch -> ch == '|').count() < 2) {
                continue;
            }

            List<String> cells = splitTableRow(line);
            if (cells.size() < 2) {
                continue;
            }

            String acKey = cells.get(0);
            if (acKey == null || acKey.isBlank() || isTableHeaderCell(acKey) || isSeparatorCell(acKey)) {
                continue;
            }

            boolean covered = cells.stream()
                    .skip(1)
                    .anyMatch(this::isAddedTestMarker);
            if (covered) {
                acKeys.add(acKey.trim());
            }
        }

        return acKeys.stream().distinct().toList();
    }

    private List<String> splitTableRow(String line) {
        String[] parts = line.split("\\|");
        List<String> cells = new ArrayList<>();
        for (String part : parts) {
            String cell = part.trim();
            if (!cell.isBlank()) {
                cells.add(cell);
            }
        }
        return cells;
    }

    private boolean isTableHeaderCell(String cell) {
        String normalized = cell == null ? "" : cell.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank()
                || normalized.equals("tc id")
                || normalized.equals("test")
                || normalized.equals("type")
                || normalized.equals("target")
                || normalized.equals("related ac")
                || normalized.equals("ac id")
                || normalized.equals("ac")
                || normalized.equals("test type")
                || normalized.equals("fe ut")
                || normalized.equals("be ut")
                || normalized.equals("api it")
                || normalized.equals("contract test")
                || normalized.equals("db/migration")
                || normalized.equals("e2e")
                || normalized.equals("black-box")
                || normalized.equals("black box");
    }

    private boolean isAddedTestMarker(String cell) {
        if (cell == null) {
            return false;
        }

        String normalized = cell.trim().toLowerCase(Locale.ROOT);
        return !normalized.isBlank()
                && !"n/a".equals(normalized)
                && !"-".equals(normalized)
                && !"na".equals(normalized)
                && !"none".equals(normalized)
                && !"not added".equals(normalized)
                && !"missing".equals(normalized);
    }

    private boolean isSeparatorCell(String cell) {
        if (cell == null) {
            return false;
        }
        String normalized = cell.trim();
        return !normalized.isBlank() && normalized.chars().allMatch(ch -> ch == '-');
    }
}
