package com.sdd.platform.domain.service.markdown.report;

import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownDocument;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownIssue;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownPlaceholder;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownSection;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownTable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ReportMarkdownParser {

    private static final String PARSER_VERSION = "report-markdown-parser-v1";
    private static final String DEFAULT_PARSE_MODE = "report";
    private static final String ACCEPTED_RISK_SECTION = "ACCEPTED_RISK";
    private static final Pattern SOURCE_PATH_PATTERN = Pattern.compile("(?i)(?:^|.*/)changes/([^/\\\\]+)/report\\.md$");

    public static final List<String> ALL_FIELDS = List.of(
            "TÓM_TẮT_THAY_ĐỔI",
            "PHẠM_VI_ẢNH_HƯỞNG",
            "KẾT_QUẢ_REVIEW",
            "KẾT_QUẢ_KIỂM_THỬ",
            "CÔNG_VIỆC_CÒN_LẠI_HÀNH_ĐỘNG_TIẾP_THEO",
            "QUY_TRÌNH_HOÀN_TÁC",
            "DANH_MỤC_ĐẦU_RA"
        );

    private final MarkdownParserCore core;

    public ReportMarkdownParser() {
        this(new MarkdownParserCore());
    }

    protected ReportMarkdownParser(MarkdownParserCore core) {
        this.core = core;
    }

    // =========================
    // Parse overloads
    // =========================

    public ParsedArtifact parse(String content) {
        return parse(content, null, DEFAULT_PARSE_MODE);
    }

    public ParsedArtifact parse(String content, String sourcePath) {
        return parse(content, sourcePath, DEFAULT_PARSE_MODE);
    }

    public ParsedArtifact parse(String content, String sourcePath, String parseMode) {
        String normalizedParseMode = normalizeParseMode(parseMode);
        MarkdownDocument document = core.parse(content != null ? content : "", sourcePath);

        List<ParsingIssue> warnings = new ArrayList<>(convertIssues(document.warnings()));
        List<ParsingIssue> errors = new ArrayList<>(convertIssues(document.errors()));
        List<MarkdownPlaceholder> placeholders = List.copyOf(document.placeholders());

        Map<String, String> frontMatter = new LinkedHashMap<>(document.frontMatter());
        Map<String, String> headerMetadata = new LinkedHashMap<>(document.headerMetadata());
        List<MarkdownSection> sectionList = List.copyOf(document.sections());
        Map<String, String> sections = materializeSections(sectionList);
        List<MarkdownTable> tables = List.copyOf(document.tables());
        List<AcceptedRiskRow> acceptedRisks = extractAcceptedRisks(tables);

        String ticketId = inferTicketId(frontMatter, headerMetadata, sourcePath);
        if (ticketId == null || ticketId.isBlank()) {
            warnings.add(new ParsingIssue(
                    "ticket_id_missing",
                    "warning",
                    "Unable to infer ticket_id from front matter, header metadata, or source path",
                    sourcePath,
                    null,
                    -1));
        }

        List<String> missingFields = detectMissingFields(sections);
        if (!missingFields.isEmpty()) {
            warnings.add(new ParsingIssue(
                    "required_fields_missing",
                    "warning",
                    "Missing required fields: " + String.join(", ", missingFields),
                    sourcePath,
                    null,
                    -1));
        }

        if (!placeholders.isEmpty()) {
            warnings.add(new ParsingIssue(
                    "placeholder_detected",
                    "warning",
                    "Placeholder values were detected in required fields",
                    sourcePath,
                    null,
                    -1));
        }

        boolean isEmpty = document.normalizedContent() == null
                || document.normalizedContent().trim().isEmpty();

        boolean artifactExists = !isEmpty;
        String artifactStatus = artifactExists ? (errors.isEmpty() ? "present" : "invalid") : "missing";
        String parseStatus = determineParseStatus(normalizedParseMode, warnings, errors);

        Map<String, Object> parsedSummary = buildParsedSummary(
                ticketId,
                normalizedParseMode,
                parseStatus,
                artifactStatus,
                document.contentHash(),
                sections,
                tables,
                warnings,
                errors,
                missingFields,
                placeholders,
                acceptedRisks);

        return new ParsedArtifact(
                sourcePath,
                ticketId,
                normalizedParseMode,
                parseStatus,
                artifactStatus,
                artifactExists,
                frontMatter,
                headerMetadata,
                sections,
                tables,
                acceptedRisks,
                placeholders,
                warnings,
                errors,
                missingFields,
                document.normalizedContent(),
                document.contentHash(),
                PARSER_VERSION,
                parsedSummary);
    }

    private List<AcceptedRiskRow> extractAcceptedRisks(List<MarkdownTable> tables) {
        if (tables == null || tables.isEmpty()) {
            return List.of();
        }
        List<AcceptedRiskRow> rows = new ArrayList<>();
        for (MarkdownTable table : tables) {
            if (!isAcceptedRiskTable(table)) {
                continue;
            }
            for (List<String> row : table.rows()) {
                AcceptedRiskRow acceptedRisk = mapAcceptedRiskRow(row);
                if (acceptedRisk != null) {
                    rows.add(acceptedRisk);
                }
            }
        }
        return List.copyOf(rows);
    }

    private boolean isAcceptedRiskTable(MarkdownTable table) {
        if (table == null || table.sectionKey() == null) {
            return false;
        }
        if (!ACCEPTED_RISK_SECTION.equalsIgnoreCase(table.sectionKey())) {
            return false;
        }
        if (table.headers() == null || table.headers().size() < 6) {
            return false;
        }
        List<String> normalized = table.headers().stream()
                .limit(6)
                .map(this::normalizeTableHeader)
                .toList();
        return normalized.equals(List.of("risk", "impact", "owner", "deadline", "status", "approver"));
    }

    private AcceptedRiskRow mapAcceptedRiskRow(List<String> row) {
        if (row == null || row.size() < 6) {
            return null;
        }
        String risk = cleanCell(row.get(0));
        String impact = cleanCell(row.get(1));
        String owner = cleanCell(row.get(2));
        String deadline = cleanCell(row.get(3));
        String status = cleanCell(row.get(4)).toUpperCase(Locale.ROOT);
        String approver = cleanCell(row.get(5));
        if (risk.isBlank() && impact.isBlank() && owner.isBlank() && deadline.isBlank() && status.isBlank()
                && approver.isBlank()) {
            return null;
        }
        if (!"OPEN".equalsIgnoreCase(status) && !"CLOSED".equalsIgnoreCase(status)) {
            return null;
        }
        return new AcceptedRiskRow(risk, impact, owner, deadline, status, approver);
    }

    private String normalizeTableHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private String cleanCell(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        if (v.startsWith("`") && v.endsWith("`") && v.length() >= 2) {
            v = v.substring(1, v.length() - 1);
        }
        return v.trim();
    }

    // =========================
    // Utility
    // =========================

    public boolean hasSection(ParsedArtifact parsed, String... aliases) {
        if (parsed == null || aliases == null || aliases.length == 0) {
            return false;
        }
        for (String alias : aliases) {
            String normalizedAlias = normalizeSectionAlias(alias);
            if (parsed.sections().keySet().stream()
                    .anyMatch(key -> key.equalsIgnoreCase(normalizedAlias) || key.contains(normalizedAlias))) {
                return true;
            }
        }
        return false;
    }

    // =========================
    // Helpers
    // =========================

    private List<ParsingIssue> convertIssues(List<MarkdownIssue> issues) {
        return issues.stream()
                .map(issue -> new ParsingIssue(
                        issue.code(),
                        issue.severity(),
                        issue.message(),
                        issue.path(),
                        issue.sectionKey(),
                        issue.line()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Map<String, String> materializeSections(List<MarkdownSection> sectionList) {
        Map<String, String> sections = new LinkedHashMap<>();
        for (MarkdownSection section : sectionList) {
            sections.put(section.canonicalKey(), buildEffectiveSectionBody(section, sectionList));
        }
        return sections;
    }

    private String buildEffectiveSectionBody(MarkdownSection parent, List<MarkdownSection> sectionList) {
        if (parent == null) {
            return "";
        }
        List<MarkdownSection> children = findDynamicChildren(sectionList, parent.canonicalKey());
        if (children.isEmpty()) {
            return parent.body();
        }
        StringBuilder merged = new StringBuilder();
        if (parent.body() != null && !parent.body().isBlank()) {
            merged.append(parent.body().trim());
        }
        for (MarkdownSection child : children) {
            String childBody = child.body() == null ? "" : child.body().trim();
            if (childBody.isBlank()) {
                continue;
            }
            if (merged.length() > 0) {
                merged.append("\n\n");
            }
            merged.append("### ").append(child.title()).append('\n').append(childBody);
        }
        return merged.toString().trim();
    }

    private List<MarkdownSection> findDynamicChildren(List<MarkdownSection> sectionList, String parentKey) {
        List<MarkdownSection> children = new ArrayList<>();
        int parentLevel = -1;
        boolean found = false;
        for (MarkdownSection section : sectionList) {
            if (!found) {
                if (section.canonicalKey().equals(parentKey)) {
                    parentLevel = section.level();
                    found = true;
                }
                continue;
            }
            if (section.level() <= parentLevel) {
                break;
            }
            if (section.level() == parentLevel + 1) {
                children.add(section);
            }
        }
        return children;
    }

    private List<String> detectMissingFields(Map<String, String> sections) {
        List<String> missing = new ArrayList<>();
        for (String field : ALL_FIELDS) {
            String value = sections.get(field);
            if (value == null || value.isBlank()) {
                missing.add("section:" + field.toLowerCase(Locale.ROOT));
            }
        }
        return missing;
    }

    private String determineParseStatus(String parseMode,
            List<ParsingIssue> warnings,
            List<ParsingIssue> errors) {
        if (!errors.isEmpty()) {
            return "FAILED";
        }
        if (!warnings.isEmpty()) {
            return "PARTIAL";
        }
        return "official".equalsIgnoreCase(parseMode) ? "OFFICIAL" : "DRAFT";
    }

    private Map<String, Object> buildParsedSummary(
            String ticketId,
            String parseMode,
            String parseStatus,
            String artifactStatus,
            String contentHash,
            Map<String, String> sections,
            List<MarkdownTable> tables,
            List<ParsingIssue> warnings,
            List<ParsingIssue> errors,
            List<String> missingFields,
            List<MarkdownPlaceholder> placeholders,
            List<AcceptedRiskRow> acceptedRisks) {
        Map<String, Object> summary = new LinkedHashMap<>();

        // identity / parse context
        summary.put("ticket_id", ticketId);
        summary.put("parse_mode", parseMode);
        summary.put("parse_status", parseStatus);
        summary.put("artifact_status", artifactStatus);
        summary.put("content_hash", contentHash);
        summary.put("parser_version", PARSER_VERSION);

        // section content
        for (String key : ALL_FIELDS) {
            summary.put(key, sections.get(key));
        }

        // legacy aliases kept for downstream compatibility while report consumers move to template v2 keys
        summary.put("EDITED_SUMMARY", resolveSection(sections, "TÓM_TẮT_THAY_ĐỔI", "EDITED_SUMMARY"));
        summary.put("SCOPE_OF_INFLUENCE", resolveSection(sections, "PHẠM_VI_ẢNH_HƯỞNG", "SCOPE_OF_INFLUENCE"));
        summary.put("REVIEW_RESULTS", resolveSection(sections, "KẾT_QUẢ_REVIEW", "REVIEW_RESULTS"));
        summary.put("TEST_RESULTS", resolveSection(sections, "KẾT_QUẢ_KIỂM_THỬ", "TEST_RESULTS"));
        summary.put("OPEN_ISSUES", resolveSection(sections, "CÔNG_VIỆC_CÒN_LẠI_HÀNH_ĐỘNG_TIẾP_THEO", "OPEN_ISSUES"));
        summary.put("ROLLBACK", resolveSection(sections, "QUY_TRÌNH_HOÀN_TÁC", "ROLLBACK"));
        summary.put("OUTPUT_ARTIFACTS", resolveSection(sections, "DANH_MỤC_ĐẦU_RA", "OUTPUT_ARTIFACTS"));

        // counts
        summary.put("section_count", sections.size());
        summary.put("table_count", tables.size());
        summary.put("warning_count", warnings.size());
        summary.put("error_count", errors.size());
        summary.put("placeholder_count", placeholders.size());
        summary.put("missing_required_count", missingFields.size());
        summary.put("has_missing_required_sections", !missingFields.isEmpty());
        summary.put("accepted_risk_count", acceptedRisks.size());
        summary.put("accepted_risk_open_count",
                acceptedRisks.stream().filter(row -> "OPEN".equalsIgnoreCase(row.status())).count());

        // detection flags
        summary.put("has_open_issue_detected", hasNonBlankSection(sections, "CÔNG_VIỆC_CÒN_LẠI_HÀNH_ĐỘNG_TIẾP_THEO", "OPEN_ISSUES"));
        summary.put("has_rollback_detected", hasNonBlankSection(sections, "QUY_TRÌNH_HOÀN_TÁC", "ROLLBACK"));

        return summary;
    }

    private String resolveSection(Map<String, String> sections, String primaryKey, String... legacyKeys) {
        String primary = sections.get(primaryKey);
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (legacyKeys == null) {
            return primary;
        }
        for (String legacyKey : legacyKeys) {
            String value = sections.get(legacyKey);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return primary;
    }

    private boolean hasNonBlankSection(Map<String, String> sections, String... keys) {
        if (sections == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            String value = sections.get(key);
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private String inferTicketId(Map<String, String> frontMatter,
            Map<String, String> headerMetadata,
            String sourcePath) {
        String frontMatterTicketId = firstNonBlank(
                frontMatter.get("ticket_id"),
                frontMatter.get("ticket-id"),
                frontMatter.get("ticketid"));
        if (isTicketId(frontMatterTicketId)) {
            return frontMatterTicketId;
        }

        String pathTicketId = ticketIdFromPath(sourcePath);
        if (isTicketId(pathTicketId)) {
            return pathTicketId;
        }

        String headerTicketId = firstNonBlank(
                headerMetadata.get("ticket_id"),
                headerMetadata.get("ticket-id"),
                headerMetadata.get("ticketid"));
        if (isTicketId(headerTicketId)) {
            return headerTicketId;
        }

        return null;
    }

    private String ticketIdFromPath(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return null;
        }
        String normalized = sourcePath.replace('\\', '/');
        Matcher matcher = SOURCE_PATH_PATTERN.matcher(normalized);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        // fallback: extract from /changes/<ticketId>/...
        int idx = normalized.indexOf("/changes/");
        if (idx < 0)
            return null;
        String remaining = normalized.substring(idx + "/changes/".length());
        String[] parts = remaining.split("/");
        return parts.length > 0 && !parts[0].isBlank() ? parts[0] : null;
    }

    private boolean isTicketId(String value) {
        return value != null && value.matches("^[A-Z0-9][A-Z0-9-]*$");
    }

    private String normalizeParseMode(String parseMode) {
        if (parseMode == null || parseMode.isBlank()) {
            return DEFAULT_PARSE_MODE;
        }
        return parseMode.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeSectionAlias(String alias) {
        if (alias == null) {
            return "";
        }
        return alias.trim()
                .replace('-', ' ')
                .replace('/', ' ')
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT)
                .replace(' ', '_');
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }

    // =========================
    // Records
    // =========================

    public record AcceptedRiskRow(
            String risk,
            String impact,
            String owner,
            String deadline,
            String status,
            String approver) {
    }

    public record ParsingIssue(
            String code,
            String severity,
            String message,
            String sourcePath,
            String sectionKey,
            int line) {
    }

    public record ParsedArtifact(
            String sourcePath,
            String ticketId,
            String parseMode,
            String parseStatus,
            String artifactStatus,
            boolean artifactExists,
            Map<String, String> frontMatter,
            Map<String, String> headerMetadata,
            Map<String, String> sections,
            List<MarkdownTable> tables,
            List<AcceptedRiskRow> acceptedRisks,
            List<MarkdownPlaceholder> placeholders,
            List<ParsingIssue> warnings,
            List<ParsingIssue> errors,
            List<String> requiredFieldsMissing,
            String normalizedContent,
            String contentHash,
            String parserVersion,
            Map<String, Object> parsedSummary) {
        public boolean containsAnywhere(String needle) {
            if (needle == null || needle.isBlank()) {
                return false;
            }
            String low = needle.toLowerCase(Locale.ROOT);
            return sections.values().stream()
                    .anyMatch(v -> v != null && v.toLowerCase(Locale.ROOT).contains(low))
                    || tables.stream().anyMatch(table -> table.rows().stream().flatMap(List::stream)
                            .anyMatch(v -> v != null && v.toLowerCase(Locale.ROOT).contains(low)));
        }
    }
}
