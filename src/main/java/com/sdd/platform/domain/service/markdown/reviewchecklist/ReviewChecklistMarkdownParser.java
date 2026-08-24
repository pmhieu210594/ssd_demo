package com.sdd.platform.domain.service.markdown.reviewchecklist;

import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownDocument;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownIssue;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownPlaceholder;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownSection;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ReviewChecklistMarkdownParser {

    private static final String PARSER_VERSION = "review-checklist-markdown-parser-v2";
    private static final String DEFAULT_PARSE_MODE = "draft";

    // Path: .../changes/<TICKET>/review-checklist.md
    private static final Pattern SOURCE_PATH_PATTERN = Pattern
            .compile("(?i)(?:^|.*/)changes/([^/\\\\]+)/review-checklist\\.md$");

    public static final List<String> ALL_FIELDS = List.of();

    public static final List<String> REQUIRED_SECTION_KEYS = List.of(
        "SPEC_AC",
        "THIẾT_KẾ_PHỤ_THUỘC",
        "BẢO_MẬT",
        "HIỆU_NĂNG",
        "TƯƠNG_THÍCH",
        "LOGGING_AUDIT",
        "XỬ_LÝ_LỖI",
        "KIỂM_THỬ",
        "VẬN_HÀNH",
        "BẢNG_ÁNH_XẠ_AC_CHECKLIST_ITEMS"
        
    );

    private static final Map<String, List<String>> PARENT_CHILD_HIERARCHY = Map.ofEntries();

    private static final Set<String> HEADING_ONLY_SECTION_KEYS = Set.copyOf(PARENT_CHILD_HIERARCHY.keySet());

    private final MarkdownParserCore core;

    public ReviewChecklistMarkdownParser() {
        this(new MarkdownParserCore());
    }

    protected ReviewChecklistMarkdownParser(MarkdownParserCore core) {
        this.core = core;
    }

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
        Map<String, String> sections = document.sectionMap();
        List<MarkdownSection> sectionList = List.copyOf(document.sections());
        List<MarkdownTable> tables = List.copyOf(document.tables());

        String ticketId = inferTicketId(frontMatter, headerMetadata, sourcePath, sections);
        if (ticketId == null || ticketId.isBlank()) {
            warnings.add(new ParsingIssue(
                    "ticket_id_missing",
                    "warning",
                    "Unable to infer ticket_id from front matter, header metadata, or source path",
                    sourcePath,
                    null,
                    -1));
        }

        List<String> missingFields = detectMissingFields(sections, sectionList);
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
                placeholders);
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
                List.of(),
                placeholders,
                warnings,
                errors,
                missingFields,
                document.normalizedContent(),
                document.contentHash(),
                PARSER_VERSION,
                parsedSummary);
    }

    // ===== private helpers =====
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

    public static List<String> requiredSectionKeys() {
        List<String> keys = new ArrayList<>();
        for (String field : ALL_FIELDS) {
            keys.add(field.toUpperCase(Locale.ROOT));
        }
        keys.addAll(REQUIRED_SECTION_KEYS);
        for (List<String> children : PARENT_CHILD_HIERARCHY.values()) {
            keys.addAll(children);
        }
        return List.copyOf(keys);
    }

    public static boolean isHeadingOnlySection(String sectionKey) {
        return sectionKey != null && HEADING_ONLY_SECTION_KEYS.contains(sectionKey);
    }

    private List<String> detectMissingFields(Map<String, String> sections,
            List<MarkdownSection> sectionList) {
        List<String> missing = new ArrayList<>();
        for (String requiredSectionKey : REQUIRED_SECTION_KEYS) {
            List<MarkdownSection> dynamicChildren = findDynamicChildren(sectionList, requiredSectionKey);
            boolean sectionPresent = sections.containsKey(requiredSectionKey);
            boolean sectionHasBody = sectionPresent && sections.get(requiredSectionKey) != null
                    && !sections.get(requiredSectionKey).trim().isEmpty();
            boolean sectionIsHeadingOnly = PARENT_CHILD_HIERARCHY.containsKey(requiredSectionKey);
            boolean sectionPresentByHeadingOnly = sectionIsHeadingOnly && sectionPresent && !dynamicChildren.isEmpty();

            if (!sectionPresent || (!sectionHasBody && !sectionPresentByHeadingOnly)) {
                missing.add("section:" + requiredSectionKey);
            }

            if (!dynamicChildren.isEmpty()) {
                for (MarkdownSection child : dynamicChildren) {
                    if (child.body() == null || child.body().trim().isEmpty()) {
                        missing.add("section:" + child.canonicalKey());
                    }
                }
            }
        }

        for (String field : ALL_FIELDS) {
            String value = sections.get(field.toUpperCase());
            if (value == null || value.isBlank()) {
                missing.add("section:" + field.toUpperCase());
            }
        }
        return missing;
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

    private String normalizeParseMode(String parseMode) {
        if (parseMode == null || parseMode.isBlank()) {
            return DEFAULT_PARSE_MODE;
        }
        return parseMode.trim().toLowerCase(Locale.ROOT);
    }

    private String inferTicketId(Map<String, String> frontMatter,
            Map<String, String> headerMetadata,
            String sourcePath,
            Map<String, String> sections) {
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
            List<MarkdownPlaceholder> placeholders) {
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
            summary.put(key, sections.get(key.toUpperCase()));
        }
        summary.put("security_review_present", sections.containsKey("SECURITY_PRIVACY_REVIEW"));
        summary.put("security_review_checked", isChecklistSectionFullyChecked(
                sections.get("SECURITY_PRIVACY_REVIEW")));
        summary.put("test_review_present", sections.containsKey("TEST_REVIEW"));
        summary.put("test_review_checked", isChecklistSectionFullyChecked(sections.get("TEST_REVIEW")));

        // counts
        summary.put("section_count", sections.size());
        summary.put("table_count", tables.size());
        summary.put("warning_count", warnings.size());
        summary.put("error_count", errors.size());
        summary.put("placeholder_count", placeholders.size());
        summary.put("missing_required_count", missingFields.size());
        summary.put("has_missing_required_sections", !missingFields.isEmpty());

        // detection flags
        summary.put("has_open_issue_detected", sections.containsKey("open_issues"));
        summary.put("has_risk_detected", sections.containsKey("risks"));
        summary.put("has_rollback_detected", sections.containsKey("rollback"));
        summary.put("has_review_checklist_structure", !sections.isEmpty());

        return summary;
    }

    private boolean isChecklistSectionFullyChecked(String sectionBody) {
        if (sectionBody == null || sectionBody.isBlank()) {
            return false;
        }

        boolean hasCheckboxItem = false;
        for (String line : sectionBody.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (isCheckedCheckboxLine(trimmed)) {
                hasCheckboxItem = true;
                continue;
            }
            if (isUncheckedCheckboxLine(trimmed)) {
                return false;
            }
        }
        return hasCheckboxItem;
    }

    private boolean isCheckedCheckboxLine(String line) {
        return line.matches("(?i)^[-*+]\\s*\\[(x|v)\\]\\s+.+");
    }

    private boolean isUncheckedCheckboxLine(String line) {
        return line.matches("(?i)^[-*+]\\s*\\[\\s*\\]\\s+.+");
    }

    // ===== DTOs =====

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
            List<?> acceptanceCriteria,
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
