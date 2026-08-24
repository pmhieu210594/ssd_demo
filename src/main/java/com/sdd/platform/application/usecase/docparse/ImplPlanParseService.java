package com.sdd.platform.application.usecase.docparse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.persistence.CiRunRepositoryPort;
import com.sdd.platform.application.port.out.persistence.DocParsePersistencePort;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreService;
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
import com.sdd.platform.domain.service.ArtifactNormalizer;
import com.sdd.platform.domain.service.ArtifactNormalizer.ParsedArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
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
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ImplPlanParseService {

    private static final Logger log = LoggerFactory.getLogger(ImplPlanParseService.class);

    public static final String DOCUMENT_TYPE = "IMPL_PLAN";
    public static final String DEFAULT_PARSER_NAME = "impl-plan-parser";
    public static final String DEFAULT_PARSER_VERSION = "v1";
    public static final String SECTION_TYPE = "impl-plan";
    private static final Pattern AC_ID_PATTERN = Pattern.compile("\\bAC-[A-Z0-9-]+\\b");

    private static final List<FieldSpec> FIELD_SPECS = List.of(
            new FieldSpec("Nguyên tắc sử dụng template", "nguyen-tac-su-dung-template", "Nguyên tắc sử dụng template", "Nguyên tắc sử dụng template",
                    "Template usage principles", true, 1),
            new FieldSpec("Implementation policy", "implementation-policy", "Implementation policy", "Implementation policy",
                    "Implementation policy", true, 2),
            new FieldSpec("Tóm tắt policy", "tom-tat-policy", "Tóm tắt policy", "Tóm tắt policy",
                    "Policy summary", true, 3),
            new FieldSpec("Non-goals / ngoài phạm vi", "non-goals-ngoai-pham-vi", "Non-goals / ngoài phạm vi", "Non-goals / ngoài phạm vi",
                    "Out of scope items", true, 4),
            new FieldSpec("Phương án đã so sánh", "phuong-an-da-so-sanh", "Phương án đã so sánh", "Phương án đã so sánh",
                    "Compared alternatives", true, 5),
            new FieldSpec("Ràng buộc từ spec-pack", "rang-buoc-tu-spec-pack", "Ràng buộc từ spec-pack", "Ràng buộc từ spec-pack",
                    "Constraints from spec-pack", true, 6),
            new FieldSpec("Impact analysis", "impact-analysis", "Impact analysis", "Impact analysis",
                    "Impact analysis", true, 7),
            new FieldSpec("Files / modules có thể bị ảnh hưởng", "files-modules-co-the-bi-anh-huong", "Files / modules có thể bị ảnh hưởng", "Files / modules có thể bị ảnh hưởng",
                    "Potentially impacted files/modules", true, 8),
            new FieldSpec("API contract có thể bị ảnh hưởng", "api-contract-co-the-bi-anh-huong", "API contract có thể bị ảnh hưởng", "API contract có thể bị ảnh hưởng",
                    "Potentially impacted API contracts", true, 9),
            new FieldSpec("DB / migration có thể bị ảnh hưởng", "db-migration-co-the-bi-anh-huong", "DB / migration có thể bị ảnh hưởng", "DB / migration có thể bị ảnh hưởng",
                    "Potentially impacted DB/migrations", true, 10),
            new FieldSpec("Settings / config / feature flags", "settings-config-feature-flags", "Settings / config / feature flags", "Settings / config / feature flags",
                    "Settings, config, and feature flags", true, 11),
            new FieldSpec("Logs / audit / monitoring", "logs-audit-monitoring", "Logs / audit / monitoring", "Logs / audit / monitoring",
                    "Logs, audit, and monitoring", true, 12),
            new FieldSpec("Permissions / roles", "permissions-roles", "Permissions / roles", "Permissions / roles",
                    "Permissions and roles", true, 13),
            new FieldSpec("Backward compatibility / data compatibility", "backward-compatibility-data-compatibility", "Backward compatibility / data compatibility", "Backward compatibility / data compatibility",
                    "Backward and data compatibility", true, 14),
            new FieldSpec("Existing code cần đọc trước", "existing-code-can-doc-truoc", "Existing code cần đọc trước", "Existing code cần đọc trước",
                    "Existing code to read first", true, 15),
            new FieldSpec("Danh sách code cần đọc", "danh-sach-code-can-doc", "Danh sách code cần đọc", "Danh sách code cần đọc",
                    "Code reading list", true, 16),
            new FieldSpec("Thứ tự đọc đề xuất", "thu-tu-doc-de-xuat", "Thứ tự đọc đề xuất", "Thứ tự đọc đề xuất",
                    "Recommended reading order", true, 17),
            new FieldSpec("Điều kiện để mở rộng phạm vi đọc", "dieu-kien-de-mo-rong-pham-vi-doc", "Điều kiện để mở rộng phạm vi đọc", "Điều kiện để mở rộng phạm vi đọc",
                    "Conditions to expand reading scope", true, 18),
            new FieldSpec("Changes / thiết kế thay đổi", "changes-thiet-ke-thay-doi", "Changes / thiết kế thay đổi", "Changes / thiết kế thay đổi",
                    "Change design", true, 19),
            new FieldSpec("Tổng quan thay đổi", "tong-quan-thay-doi", "Tổng quan thay đổi", "Tổng quan thay đổi",
                    "Change overview", true, 20),
            new FieldSpec("Implementation steps", "implementation-steps", "Implementation steps", "Implementation steps",
                    "Implementation steps", true, 21),
            new FieldSpec("Risks & mitigation", "risks-mitigation", "Risks & mitigation", "Risks & mitigation",
                    "Risks and mitigation", true, 22),
            new FieldSpec("Rollback plan", "rollback-plan", "Rollback plan", "Rollback plan",
                    "Rollback plan", true, 23),
            new FieldSpec("Code rollback", "code-rollback", "Code rollback", "Code rollback",
                    "Code rollback strategy", true, 24),
            new FieldSpec("DB rollback", "db-rollback", "DB rollback", "DB rollback",
                    "Database rollback strategy", true, 25),
            new FieldSpec("Config / feature flag rollback", "config-feature-flag-rollback", "Config / feature flag rollback", "Config / feature flag rollback",
                    "Configuration/feature flag rollback", true, 26),
            new FieldSpec("Verification procedure", "verification-procedure", "Verification procedure", "Verification procedure",
                    "Verification procedure", true, 27),
            new FieldSpec("Automated verification", "automated-verification", "Automated verification", "Automated verification",
                    "Automated verification", true, 28),
            new FieldSpec("Manual verification matrix", "manual-verification-matrix", "Manual verification matrix", "Manual verification matrix",
                    "Manual verification matrix", true, 29),
            new FieldSpec("Regression checks", "regression-checks", "Regression checks", "Regression checks",
                    "Regression checks", true, 30),
            new FieldSpec("Open Issues / Cần xác nhận trước khi implementation", "open-issues-can-xac-nhan-truoc-khi-implementation", "Open Issues / Cần xác nhận trước khi implementation", "Open Issues / Cần xác nhận trước khi implementation",
                    "Open issues before implementation", true, 31),
            new FieldSpec("AC mapping table", "ac-mapping-table", "AC mapping table", "AC mapping table",
                    "AC mapping table", true, 32),
            new FieldSpec("Output", "output", "Output", "Output",
                    "Output", true, 33)
    );

    private final ArtifactNormalizer artifactNormalizer;
    private final DocParsePersistencePort persistence;
    private final ObjectMapper objectMapper;
    private final CiRunRepositoryPort ciRunRepositoryPort;
    private final EvidenceQualityScoreService evidenceQualityScoreService;

    @Autowired
    public ImplPlanParseService(ArtifactNormalizer artifactNormalizer,
                                DocParsePersistencePort persistence,
                                ObjectMapper objectMapper,
                                CiRunRepositoryPort ciRunRepositoryPort,
                                EvidenceQualityScoreService evidenceQualityScoreService) {
        this.artifactNormalizer = artifactNormalizer;
        this.persistence = persistence;
        this.objectMapper = objectMapper;
        this.ciRunRepositoryPort = ciRunRepositoryPort;
        this.evidenceQualityScoreService = evidenceQualityScoreService;
    }

    public ImplPlanParseService(ArtifactNormalizer artifactNormalizer,
                                DocParsePersistencePort persistence,
                                ObjectMapper objectMapper,
                                CiRunRepositoryPort ciRunRepositoryPort) {
        this(artifactNormalizer, persistence, objectMapper, ciRunRepositoryPort, null);
    }

    @Transactional
    public ParseResult parseAndStore(ParseRequest request) {
        log.info("ImplPlan parse start repoId={} ticketId={} mode={} path={} traceId={}",
                request.repositoryId(),
                request.ticketId(),
                request.parseMode(),
                request.sourcePath(),
                request.traceId());
        ParseResult parsed = parse(request);
        ParseSnapshot snapshot = persistence.upsertSnapshot(buildSnapshot(request, parsed));
        List<ParseField> savedSections = buildSections(snapshot.artifactSnapshotId(), request.ticketId(), parsed);
        persistence.replaceSections(snapshot.artifactSnapshotId(), request.ticketId(), savedSections);
        persistence.persistEvidenceEvent(buildEvidenceEvent(request, snapshot, parsed));
        ParseDataQuality quality = buildDataQuality(request, snapshot, parsed);
        persistence.persistDataQuality(quality);
        if (evidenceQualityScoreService != null && request.ticketId() != null) {
            evidenceQualityScoreService.recalculateFromParser(request.ticketId(), null, request.traceId());
        }
        log.info("ImplPlan parse stored repoId={} ticketId={} path={} status={} snapshotId={} sections={}",
                request.repositoryId(),
                request.ticketId(),
                request.sourcePath(),
                parsed.parseStatus(),
                snapshot.artifactSnapshotId(),
                savedSections.size());

        return new ParseResult(
                snapshot,
                savedSections,
                parsed.parseStatus(),
                parsed.missingFields(),
                parsed.warnings(),
                parsed.sourceHash(),
                parsed.fieldValues()
        );
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
                if (!section.presentFlag()) {
                    missing.add(section.sectionKey());
                }
            }
            List<String> warnings = sections.stream()
                    .map(ParseField::parseWarning)
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .toList();
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

    private ParseSnapshot buildSnapshot(ParseRequest request, ParseResult parsed) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ParseSnapshot previousSnapshot = persistence.findLatestSnapshot(request.ticketId(), DOCUMENT_TYPE,
                request.parseMode()).orElse(null);
        Integer schemaVersion = resolveSchemaVersion(previousSnapshot, parsed.sourceHash());
        return new ParseSnapshot(
                null,
                request.ticketId(),
                request.repositoryId(),
                null,
                null,
                DOCUMENT_TYPE,
                "Implementation Plan",
                request.parseMode().name(),
                parsed.parseStatus().name(),
                request.sourcePath(),
                parsed.sourceHash(),
                schemaVersion,
                parsed.parseStatus() == ParseStatus.SUCCESS,
                request.sourceText() != null && request.sourceText().isBlank(),
                parsed.missingFields(),
                buildParsedSummaryJson(request, parsed),
                request.parserVersion(),
                request.connectorRunId(),
                request.sourceUpdatedAt(),
                now
        );
    }

    private ParseResult parse(ParseRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.sourceText() == null) {
            return emptyResult(ParseStatus.NOT_FOUND, null, List.of("source_text_missing"), List.of("source_text_missing"));
        }
        if (request.sourceText().isBlank()) {
            return emptyResult(ParseStatus.PARSE_ERROR, sha256(request.sourceText()), List.of("source_text_empty"), List.of("source_text_empty"));
        }

        ParsedArtifact parsedArtifact = artifactNormalizer.parse(request.sourceText());
        Map<String, String> sections = parsedArtifact.sections();
        Map<String, String> fieldValues = new LinkedHashMap<>();
        List<String> missingFields = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Integer> headingCounts = countHeadingOccurrences(request.sourceText());

        for (FieldSpec spec : FIELD_SPECS) {
            String value = sections.get(spec.sectionKey());
            boolean present = value != null && !value.isBlank();
            int count = headingCounts.getOrDefault(spec.sectionKey(), 0);
            String extractionStatus;
            String warning = null;
            if (!present) {
                extractionStatus = "MISSING";
                missingFields.add(spec.fieldKey());
                if (spec.required()) {
                    warning = "REQUIRED_SECTION_MISSING";
                    warnings.add(spec.fieldKey() + ":missing");
                }
            } else if (count > 1) {
                extractionStatus = "PARTIAL";
                warning = "DUPLICATE_HEADING";
                warnings.add(spec.fieldKey() + ":duplicate");
            } else {
                extractionStatus = "FOUND";
            }
            fieldValues.put(spec.fieldKey(), value);
        }

        ParseStatus status;
        if (missingFields.size() == FIELD_SPECS.size()) {
            status = ParseStatus.NOT_FOUND;
        } else if (!missingFields.isEmpty() || warnings.stream().anyMatch(item -> item.contains("duplicate"))) {
            status = ParseStatus.PARTIAL;
        } else {
            status = ParseStatus.SUCCESS;
        }

        List<String> summaryWarnings = new ArrayList<>(warnings);
        if (status == ParseStatus.PARTIAL && summaryWarnings.isEmpty()) {
            summaryWarnings.add("partial_parse");
        }

        List<ParseField> fields = buildSections(null, request.ticketId(), fieldValues, warnings, status);
        return new ParseResult(null, fields, status, missingFields, summaryWarnings, parsedArtifact.contentHash(), fieldValues);
    }

    private List<ParseField> buildSections(UUID snapshotId, UUID ticketId, ParseResult parsed) {
        return buildSections(snapshotId, ticketId, parsed.fieldValues(), parsed.warnings(), parsed.parseStatus());
    }

    private List<ParseField> buildSections(UUID snapshotId, UUID ticketId, Map<String, String> fieldValues, List<String> warnings, ParseStatus status) {
        List<ParseField> sections = new ArrayList<>();
        for (FieldSpec spec : FIELD_SPECS) {
            String value = fieldValues.get(spec.fieldKey());
            boolean present = value != null && !value.isBlank();
            boolean duplicate = warnings.stream().anyMatch(item -> item.startsWith(spec.fieldKey() + ":duplicate"));
            String extractionStatus;
            String warning = null;
            if (!present) {
                extractionStatus = status == ParseStatus.NOT_FOUND ? "MISSING" : "MISSING";
                warning = "REQUIRED_SECTION_MISSING";
            } else if (duplicate) {
                extractionStatus = "PARTIAL";
                warning = "DUPLICATE_HEADING";
            } else {
                extractionStatus = "FOUND";
            }
            sections.add(new ParseField(
                    null,
                    snapshotId,
                    ticketId,
                    SECTION_TYPE,
                    spec.fieldKey(),
                    value == null ? null : sha256(value),
                    value,
                    spec.required(),
                    present,
                    "FOUND".equals(extractionStatus),
                    warning
            ));
        }
        sections.sort(Comparator.comparingInt(field -> fieldValuesOrder(field.sectionKey())));
        return sections;
    }

    private int fieldValuesOrder(String fieldKey) {
        for (FieldSpec spec : FIELD_SPECS) {
            if (spec.fieldKey().equals(fieldKey)) {
                return spec.displayOrder();
            }
        }
        return Integer.MAX_VALUE;
    }

    private ParseResult emptyResult(ParseStatus status, String sourceHash, List<String> missingFields, List<String> warnings) {
        Map<String, String> fieldValues = new LinkedHashMap<>();
        for (FieldSpec spec : FIELD_SPECS) {
            fieldValues.put(spec.fieldKey(), null);
        }
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
        int schemaViolationCount = (int) parsed.warnings().stream()
                .filter(w -> w.contains(":duplicate") || w.contains(":invalid_order"))
                .count();
        List<String> errorParts = new ArrayList<>();
        if (parseErrorCount > 0) errorParts.add("parse_error");
        errorParts.addAll(parsed.missingFields());
        String errorSummary = errorParts.isEmpty() ? null : String.join("; ", errorParts);
        return new ParseDataQuality(
                request.projectId(),
                request.repositoryId(),
                request.connectorRunId(),
                DOCUMENT_TYPE + "_PARSE",
                snapshot.sourcePath(),
                missingCount,
                parseErrorCount,
                schemaViolationCount,
                calculateFreshnessDelayMinutes(snapshot.sourceUpdatedAt()),
                errorSummary,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private Integer calculateFreshnessDelayMinutes(OffsetDateTime sourceUpdatedAt) {
        if (sourceUpdatedAt == null) {
            return null;
        }
        long minutes = Duration.between(sourceUpdatedAt, OffsetDateTime.now(ZoneOffset.UTC)).toMinutes();
        return (int) Math.max(minutes, 0L);
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
        summary.put("schemaVersion", resolveSchemaVersion(
                persistence.findLatestSnapshot(request.ticketId(), DOCUMENT_TYPE, request.parseMode()).orElse(null),
                parsed.sourceHash()));
        summary.put("parserName", request.parserName());
        summary.put("parserVersion", request.parserVersion());
        summary.put("traceId", request.traceId());
        summary.put("ciGateStatus", request.ciGateStatus());
        appendCiRunMetadata(summary, resolveCiRunMetadata(request));
        summary.put("parsedFieldCount", parsed.fieldValues().values().stream().filter(value -> value != null && !value.isBlank()).count());
        summary.put("missingFieldCount", parsed.missingFields().size());
        summary.put("missingFields", parsed.missingFields());
        summary.put("warnings", parsed.warnings());
        summary.put("fieldValues", parsed.fieldValues());
        String correspondingAcTable = parsed.fieldValues().get("AC mapping table");
        summary.put("correspondingAcTableMappedAcCount", countDistinctAcIds(correspondingAcTable));
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Integer resolveSchemaVersion(ParseSnapshot previousSnapshot, String sourceHash) {
        if (sourceHash == null || sourceHash.isBlank()) {
            return null;
        }
        if (previousSnapshot == null || previousSnapshot.schemaVersion() == null) {
            return 1;
        }
        if (sourceHash.equals(previousSnapshot.contentHash())) {
            return previousSnapshot.schemaVersion();
        }
        return previousSnapshot.schemaVersion() + 1;
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
        target.put("jobName", null);
        target.put("externalRunId", view.externalRunId());
        target.put("externalJobId", null);
        target.put("ciUrl", view.ciUrl());
        target.put("ciStatus", view.status());
        target.put("ciCollectedAt", view.collectedAt());
    }

    private ParseStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return ParseStatus.PARSE_ERROR;
        }
        try {
            return ParseStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return ParseStatus.PARSE_ERROR;
        }
    }

    private Map<String, Integer> countHeadingOccurrences(String content) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (content == null || content.isBlank()) {
            return counts;
        }
        String[] lines = content.split("\\R", -1);
        for (String line : lines) {
            if (line.startsWith("## ") || line.startsWith("### ")) {
                String heading = line.replaceFirst("^#+\\s+", "");
                String canonical = canonicalizeHeading(heading);
                if (!canonical.isBlank()) {
                    counts.merge(canonical, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    private String canonicalizeHeading(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT).trim();
        normalized = normalized.replaceFirst("^\\d+[\\p{Punct}\\s]*", "");
        normalized = normalized.replaceAll("[ï¼/]", "-");
        normalized = normalized.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-");
        normalized = normalized.replaceAll("-+", "-");
        return normalized.replaceAll("^-|-$", "");
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? "parse_error" : throwable.getMessage();
        if (message == null || message.isBlank()) {
            return "parse_error";
        }
        return message.length() > 240 ? message.substring(0, 240) : message;
    }

    private String joinWarnings(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return null;
        }
        return String.join(", ", warnings);
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

    private int countDistinctAcIds(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        Matcher matcher = AC_ID_PATTERN.matcher(content);
        Set<String> ids = new LinkedHashSet<>();
        while (matcher.find()) {
            ids.add(matcher.group().toUpperCase(Locale.ROOT));
        }
        return ids.size();
    }
}
