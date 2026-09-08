package com.sdd.platform.domain.service.markdown.reviewchecklist;

import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownDocument;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownIssue;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownPlaceholder;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownSection;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ReviewChecklistMarkdownParser {

    private static final String PARSER_VERSION = "review-checklist-markdown-parser-v2";
    private static final String DEFAULT_PARSE_MODE = "draft";

    // Path: .../changes/<TICKET>/review-checklist.md
    private static final Pattern SOURCE_PATH_PATTERN = Pattern
            .compile("(?i)(?:^|.*/)changes/([^/\\\\]+)/review-checklist\\.md$");
    private static final String AC_CHECKLIST_MAPPING_SECTION_KEY = "BẢNG_ÁNH_XẠ_AC_CHECKLIST_ITEMS";
    private static final List<String> STATUS_HEADERS = List.of("Trạng thái", "Trang thai", "Status");
    private static final List<String> SEVERITY_HEADERS = List.of("Mức độ", "Muc do", "Severity");
    private static final List<String> AC_MAPPING_CHECKLIST_HEADERS = List.of(
            "Các hạng mục checklist xác nhận",
            "Cac hang muc checklist xac nhan",
            "Checklist items");

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

    private static final List<String> CHECKLIST_SECTION_KEYS = REQUIRED_SECTION_KEYS.stream()
            .filter(key -> !AC_CHECKLIST_MAPPING_SECTION_KEY.equals(key))
            .toList();

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
        Map<String, Boolean> checklistSectionPresent = new LinkedHashMap<>();
        Map<String, Boolean> checklistSectionChecked = new LinkedHashMap<>();
        Map<String, Integer> checklistSectionItemCount = new LinkedHashMap<>();
        Map<String, Integer> checklistSectionCheckedItemCount = new LinkedHashMap<>();
        Map<String, Integer> checklistSectionUncheckedItemCount = new LinkedHashMap<>();
        Map<String, Integer> uncheckedSeverityCount = new LinkedHashMap<>();
        int checkedSectionCount = 0;
        int checkableSectionCount = 0;
        int totalChecklistItemCount = 0;
        int totalCheckedChecklistItemCount = 0;
        int totalUncheckedChecklistItemCount = 0;
        for (String requiredSectionKey : CHECKLIST_SECTION_KEYS) {
            String sectionBody = sections.get(requiredSectionKey);
            boolean present = hasNonBlankSection(sections, requiredSectionKey);
            ChecklistSectionStats stats = analyzeChecklistSection(sectionBody, tables, requiredSectionKey);
            boolean checked = stats.fullyChecked();
            checklistSectionPresent.put(requiredSectionKey, present);
            checklistSectionChecked.put(requiredSectionKey, checked);
            checklistSectionItemCount.put(requiredSectionKey, stats.totalItemCount());
            checklistSectionCheckedItemCount.put(requiredSectionKey, stats.checkedItemCount());
            checklistSectionUncheckedItemCount.put(requiredSectionKey, stats.uncheckedItemCount());
            mergeUncheckedSeverityCounts(uncheckedSeverityCount, stats.uncheckedSeverityCount());
            checkableSectionCount++;
            totalChecklistItemCount += stats.totalItemCount();
            totalCheckedChecklistItemCount += stats.checkedItemCount();
            totalUncheckedChecklistItemCount += stats.uncheckedItemCount();
            if (checked) {
                checkedSectionCount++;
            }
        }

        AcChecklistMappingStats mappingStats = analyzeAcChecklistMapping(
                sections.get(AC_CHECKLIST_MAPPING_SECTION_KEY),
                tables);

        summary.put("checklist_section_present", checklistSectionPresent);
        summary.put("checklist_section_checked", checklistSectionChecked);
        summary.put("checklist_section_item_count", checklistSectionItemCount);
        summary.put("checklist_section_checked_item_count", checklistSectionCheckedItemCount);
        summary.put("checklist_section_unchecked_item_count", checklistSectionUncheckedItemCount);
        summary.put("checklist_checked_section_count", checkedSectionCount);
        summary.put("checklist_total_checkable_section_count", checkableSectionCount);
        summary.put("checklist_total_item_count", totalChecklistItemCount);
        summary.put("checklist_checked_item_count", totalCheckedChecklistItemCount);
        summary.put("checklist_unchecked_item_count", totalUncheckedChecklistItemCount);
        summary.put("checklist_unchecked_severity_count", uncheckedSeverityCount);
        summary.put("all_checklist_sections_checked",
                checkableSectionCount > 0 && checkedSectionCount == checkableSectionCount);
        summary.put("security_review_present", checklistSectionPresent.getOrDefault("BẢO_MẬT", false));
        summary.put("security_review_checked", checklistSectionChecked.getOrDefault("BẢO_MẬT", false));
        summary.put("test_review_present", checklistSectionPresent.getOrDefault("KIỂM_THỬ", false));
        summary.put("test_review_checked", checklistSectionChecked.getOrDefault("KIỂM_THỬ", false));
        summary.put("ac_checklist_mapping_present", mappingStats.present());
        summary.put("ac_checklist_mapping_row_count", mappingStats.rowCount());
        summary.put("ac_checklist_mapping_complete", mappingStats.complete());

        // counts
        summary.put("section_count", sections.size());
        summary.put("table_count", tables.size());
        summary.put("warning_count", warnings.size());
        summary.put("error_count", errors.size());
        summary.put("placeholder_count", placeholders.size());
        summary.put("missing_required_count", missingFields.size());
        summary.put("has_missing_required_sections", !missingFields.isEmpty());

        // detection flags
        summary.put("has_open_issue_detected", hasNonBlankSection(sections, "open_issues"));
        summary.put("has_risk_detected", hasNonBlankSection(sections, "risks"));
        summary.put("has_rollback_detected", hasNonBlankSection(sections, "rollback"));
        summary.put("has_review_checklist_structure", !sections.isEmpty());

        return summary;
    }

    private ChecklistSectionStats analyzeChecklistSection(String sectionBody,
            List<MarkdownTable> tables,
            String sectionKey) {
        if (sectionBody == null || sectionBody.isBlank()) {
            return ChecklistSectionStats.empty();
        }

        List<ChecklistItemStatus> tableStatuses = new ArrayList<>();
        if (tables != null && sectionKey != null && !sectionKey.isBlank()) {
            for (MarkdownTable table : tables) {
                if (!sectionKey.equals(table.sectionKey())) {
                    continue;
                }
                int statusIndex = findHeaderIndex(table.headers(), STATUS_HEADERS);
                if (statusIndex < 0) {
                    continue;
                }
                int severityIndex = findHeaderIndex(table.headers(), SEVERITY_HEADERS);
                for (List<String> row : table.rows()) {
                    String status = statusIndex < row.size() ? row.get(statusIndex) : null;
                    if (status == null || status.isBlank()) {
                        continue;
                    }
                    String severity = severityIndex >= 0 && severityIndex < row.size() ? row.get(severityIndex) : null;
                    ChecklistItemStatus itemStatus = statusFromTableCell(status, severity);
                    if (itemStatus == null) {
                        return ChecklistSectionStats.empty();
                    }
                    tableStatuses.add(itemStatus);
                }
            }
        }

        if (!tableStatuses.isEmpty()) {
            return summarizeChecklistStatuses(tableStatuses);
        }

        List<ChecklistItemStatus> checkboxStatuses = new ArrayList<>();
        for (String line : sectionBody.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (isCheckedCheckboxLine(trimmed)) {
                checkboxStatuses.add(new ChecklistItemStatus(true, null));
                continue;
            }
            if (isUncheckedCheckboxLine(trimmed)) {
                checkboxStatuses.add(new ChecklistItemStatus(false, null));
            }
        }
        return summarizeChecklistStatuses(checkboxStatuses);
    }

    private ChecklistSectionStats summarizeChecklistStatuses(List<ChecklistItemStatus> items) {
        if (items == null || items.isEmpty()) {
            return ChecklistSectionStats.empty();
        }
        int checkedItemCount = 0;
        int uncheckedItemCount = 0;
        Map<String, Integer> uncheckedSeverityCount = new LinkedHashMap<>();
        for (ChecklistItemStatus item : items) {
            if (item.checked()) {
                checkedItemCount++;
                continue;
            }
            uncheckedItemCount++;
            if (item.severity() != null && !item.severity().isBlank()) {
                uncheckedSeverityCount.merge(item.severity(), 1, Integer::sum);
            }
        }
        return new ChecklistSectionStats(
                checkedItemCount + uncheckedItemCount,
                checkedItemCount,
                uncheckedItemCount,
                uncheckedItemCount == 0 && checkedItemCount > 0,
                Collections.unmodifiableMap(uncheckedSeverityCount));
    }

    private ChecklistItemStatus statusFromTableCell(String status, String severity) {
        if (isUncheckedStatusCell(status)) {
            return new ChecklistItemStatus(false, normalizeSeverity(severity));
        }
        if (isCheckedStatusCell(status)) {
            return new ChecklistItemStatus(true, normalizeSeverity(severity));
        }
        return null;
    }

    private String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return null;
        }
        return severity.trim().toUpperCase(Locale.ROOT);
    }

    private void mergeUncheckedSeverityCounts(Map<String, Integer> aggregate, Map<String, Integer> addition) {
        if (aggregate == null || addition == null || addition.isEmpty()) {
            return;
        }
        addition.forEach((severity, count) -> {
            if (severity == null || severity.isBlank() || count == null || count <= 0) {
                return;
            }
            aggregate.merge(severity, count, Integer::sum);
        });
    }

    private AcChecklistMappingStats analyzeAcChecklistMapping(String sectionBody, List<MarkdownTable> tables) {
        boolean sectionPresent = sectionBody != null && !sectionBody.isBlank();
        if (tables == null || tables.isEmpty()) {
            return new AcChecklistMappingStats(sectionPresent, 0, false);
        }
        for (MarkdownTable table : tables) {
            if (!AC_CHECKLIST_MAPPING_SECTION_KEY.equals(table.sectionKey())) {
                continue;
            }
            int acIndex = findHeaderIndex(table.headers(), List.of("AC"));
            int checklistIndex = findHeaderIndex(table.headers(), AC_MAPPING_CHECKLIST_HEADERS);
            if (acIndex < 0 || checklistIndex < 0) {
                continue;
            }
            int completeRowCount = 0;
            for (List<String> row : table.rows()) {
                String acValue = acIndex < row.size() ? row.get(acIndex) : null;
                String checklistValue = checklistIndex < row.size() ? row.get(checklistIndex) : null;
                if (acValue != null && !acValue.isBlank() && checklistValue != null && !checklistValue.isBlank()) {
                    completeRowCount++;
                }
            }
            return new AcChecklistMappingStats(true, table.rows().size(), completeRowCount == table.rows().size()
                    && table.rows().size() > 0);
        }
        return new AcChecklistMappingStats(sectionPresent, 0, false);
    }

    private int findHeaderIndex(List<String> headers, List<String> candidateHeaders) {
        if (headers == null || headers.isEmpty() || candidateHeaders == null || candidateHeaders.isEmpty()) {
            return -1;
        }
        List<String> normalizedCandidates = candidateHeaders.stream()
                .map(this::normalizeComparisonText)
                .toList();
        for (int i = 0; i < headers.size(); i++) {
            String normalizedHeader = normalizeComparisonText(headers.get(i));
            if (normalizedCandidates.contains(normalizedHeader)) {
                return i;
            }
        }
        return -1;
    }

    private String normalizeComparisonText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return normalized.replaceAll("\\s+", " ");
    }

    private boolean hasNonBlankSection(Map<String, String> sections, String key) {
        if (sections == null || key == null || key.isBlank()) {
            return false;
        }
        String value = sections.get(key);
        return value != null && !value.isBlank();
    }

    private boolean isCheckedStatusCell(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.matches("(?i)^\\[(x|v)\\]$");
    }

    private boolean isUncheckedStatusCell(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        return trimmed.matches("(?i)^\\[\\s*\\]$");
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

    private record ChecklistItemStatus(boolean checked, String severity) {
    }

    private record ChecklistSectionStats(
            int totalItemCount,
            int checkedItemCount,
            int uncheckedItemCount,
            boolean fullyChecked,
            Map<String, Integer> uncheckedSeverityCount) {
        private static ChecklistSectionStats empty() {
            return new ChecklistSectionStats(0, 0, 0, false, Map.of());
        }
    }

    private record AcChecklistMappingStats(boolean present, int rowCount, boolean complete) {
    }
}
