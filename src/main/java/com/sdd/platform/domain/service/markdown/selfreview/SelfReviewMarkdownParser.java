package com.sdd.platform.domain.service.markdown.selfreview;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownDocument;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownIssue;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownPlaceholder;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownSection;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownTable;

/**
 * Dedicated parser for {@code self-review.md}.
 *
 * The shared Markdown parsing work lives in {@link MarkdownParserCore}; this
 * parser specializes that output into the fixed 11-section self-review
 * envelope.
 */
public class SelfReviewMarkdownParser {

    private static final Pattern SOURCE_PATH_PATTERN = Pattern
            .compile("(?i)(?:^|.*/)changes/([^/\\\\]+)/self-review\\.md$");
    private static final Pattern TICKET_ID_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9-]*$");
    private static final Pattern VERDICT_PATTERN = Pattern.compile("(?i)^(PASS|NEEDS_UPDATE|BLOCKED)$");
    private static final String DEFAULT_PARSE_MODE = "draft";
    private static final String PARSER_VERSION = "markdown-core-v1+self-review-v2";

    private static final List<String> CANONICAL_SECTION_KEYS = List.of(
            "TRẠNG_THÁI_HOÀN_THÀNH_AC",
            "CÁC_HẠNG_MỤC_CHECKLIST_(TỪ_`REVIEW-CHECKLIST.MD`)",
            "CÁC_LỆNH_ĐÃ_CHẠY",
            "LINT",
            "TYPE-CHECK",
            "UNIT_TEST",
            "BUILD",
            "TỔNG_QUAN_DIFF",
            "RỦI_RO_ĐÃ_BIẾT_CHƯA_BAO_PHỦ_CÔNG_VIỆC_CÒN_LẠI",
            "KNOWN_RISKS",
            "NOT_HANDLED_YET",
            "REMAINING_ISSUES_NỢ_KỸ_THUẬT",
            "OPEN_ISSUES_TỪ_IMPL-PLAN_VẪN_CÒN",
            "CONFIRMATIONS_CUỐI_CÙNG"
        );

    private static final Set<String> TABLE_SECTION_KEYS = Set.of(
            "TRẠNG_THÁI_HOÀN_THÀNH_AC",
            "LIST_OF_CHANGED_FILES",
            "CÁC_LỆNH_ĐÃ_CHẠY",
            "CÁC_HẠNG_MỤC_CHECKLIST_(TỪ_`REVIEW-CHECKLIST.MD`)",
            "TỔNG_QUAN_DIFF",
            "CONFIRMATIONS_CUỐI_CÙNG",
            "OPEN_ISSUES_TỪ_IMPL-PLAN_VẪN_CÒN",
            "KNOWN_RISKS",
            "NOT_HANDLED_YET",
            "REMAINING_ISSUES_NỢ_KỸ_THUẬT");

    private static final Set<String> OPTIONAL_SECTION_KEYS = Set.of();

    private static final Set<String> FREE_TEXT_SECTION_KEYS = Set.of();

    private final MarkdownParserCore core;

    public SelfReviewMarkdownParser() {
        this(new MarkdownParserCore());
    }

    protected SelfReviewMarkdownParser(MarkdownParserCore core) {
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
        MarkdownDocument document = core.parse(content, sourcePath);

        List<ParsingIssue> warnings = new ArrayList<>(convertIssues(document.warnings()));
        List<ParsingIssue> errors = new ArrayList<>(convertIssues(document.errors()));
        List<MarkdownPlaceholder> placeholders = List.copyOf(document.placeholders());

        Map<String, String> frontMatter = new LinkedHashMap<>(document.frontMatter());
        Map<String, String> headerMetadata = new LinkedHashMap<>(document.headerMetadata());
        List<MarkdownSection> sections = List.copyOf(document.sections());
        List<MarkdownTable> tables = List.copyOf(document.tables());
        List<MarkdownSection> normalizedSections = sections.stream()
                .map(this::normalizeSection)
                .toList();
        List<MarkdownTable> normalizedTables = tables.stream()
                .map(this::normalizeTable)
                .toList();

        String ticketId = inferTicketId(frontMatter, headerMetadata, sourcePath, normalizedSections);
        if (ticketId == null || ticketId.isBlank()) {
            warnings.add(issue(
                    "ticket_id_missing",
                    "Unable to infer ticket_id from front matter, header metadata, or source path",
                    sourcePath,
                    null,
                    -1));
        }

        checkSectionOrder(normalizedSections, warnings, sourcePath);
        checkNestedDepth(normalizedSections, warnings, sourcePath);

        List<MarkdownSection> canonicalSections = normalizedSections.stream()
                .filter(section -> isCanonicalSection(section.canonicalKey()))
                .filter(section -> section.level() >= 2)
                .collect(Collectors.toCollection(ArrayList::new));

        List<String> requiredSectionsMissing = detectRequiredSectionsMissing(canonicalSections, normalizedTables,
                warnings, sourcePath);
        String finalVerdict = extractFinalVerdict(canonicalSections, warnings, errors, sourcePath);

        if (!placeholders.isEmpty()) {
            warnings.add(issue(
                    "placeholder_detected",
                    "Placeholder values were detected in the document",
                    sourcePath,
                    null,
                    -1));
        }

        boolean artifactExists = !document.normalizedContent().isBlank();
        String artifactStatus = artifactExists ? (errors.isEmpty() ? "present" : "invalid") : "missing";
        String parseStatus = determineParseStatus(normalizedParseMode, warnings, errors);

        Map<String, Object> parsedSummary = buildParsedSummary(
                ticketId,
                normalizedParseMode,
                parseStatus,
                artifactStatus,
                document.contentHash(),
                canonicalSections,
                normalizedTables,
                finalVerdict,
                warnings,
                errors,
                requiredSectionsMissing,
                placeholders);

        List<MarkdownSection> freeTextSections = canonicalSections.stream()
                .filter(section -> FREE_TEXT_SECTION_KEYS.contains(section.canonicalKey()))
                .toList();
        List<MarkdownTable> tableSections = normalizedTables.stream()
                .filter(table -> TABLE_SECTION_KEYS.contains(table.sectionKey()))
                .toList();

        List<ParsedExceptionRecord> exceptionRecords = extractExceptionRecords(normalizedTables, warnings, sourcePath);

        return new ParsedArtifact(
                sourcePath,
                ticketId,
                normalizedParseMode,
                parseStatus,
                artifactStatus,
                artifactExists,
                frontMatter,
                headerMetadata,
                normalizedSections,
                freeTextSections,
                normalizedTables,
                tableSections,
                placeholders,
                warnings,
                errors,
                requiredSectionsMissing,
                finalVerdict,
                document.normalizedContent(),
                document.contentHash(),
                PARSER_VERSION,
                parsedSummary,
                exceptionRecords);
    }

    public boolean hasSection(ParsedArtifact parsed, String... aliases) {
        if (parsed == null || aliases == null || aliases.length == 0) {
            return false;
        }
        for (String alias : aliases) {
            String normalizedAlias = normalizeSectionAlias(alias);
            if (parsed.sections().stream()
                    .anyMatch(section -> section.canonicalKey().equalsIgnoreCase(normalizedAlias))) {
                return true;
            }
        }
        return false;
    }

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

    private List<String> detectRequiredSectionsMissing(List<MarkdownSection> sections,
            List<MarkdownTable> tables,
            List<ParsingIssue> warnings,
            String sourcePath) {
        List<String> missing = new ArrayList<>();
        Set<String> presentSectionKeys = sections.stream()
                .map(MarkdownSection::canonicalKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> tableSectionKeys = tables.stream()
                .map(table -> normalizeCanonicalSectionKey(table.sectionKey()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (String key : CANONICAL_SECTION_KEYS) {
            if (OPTIONAL_SECTION_KEYS.contains(key)) {
                continue;
            }
            boolean present = presentSectionKeys.contains(key);
            if (!present) {
                missing.add("section:" + key);
                continue;
            }
            if (TABLE_SECTION_KEYS.contains(key) && !tableSectionKeys.contains(key)) {
                missing.add("section:" + key + " table");
                warnings.add(issue(
                        "table_invalid",
                        "Expected a table in section " + key + " but none was parsed",
                        sourcePath,
                        key,
                        -1));
                continue;
            }
            if (TABLE_SECTION_KEYS.contains(key) && !hasMeaningfulTableContent(tables, key)) {
                missing.add("section:" + key);
                warnings.add(issue(
                        "table_empty",
                        "Expected at least one meaningful data row in section " + key,
                        sourcePath,
                        key,
                        -1));
                continue;
            }
            if (isPlaceholderOnlySection(sections, key)) {
                missing.add("section:" + key);
            }
        }

        if (!presentSectionKeys.contains("AI_GENERATED_PREDICTIONS")) {
            warnings.add(issue(
                    "optional_section_missing",
                    "Optional section AI_GENERATED_PREDICTIONS is missing",
                    sourcePath,
                    "AI_GENERATED_PREDICTIONS",
                    -1));
        } else if (isPlaceholderOnlySection(sections, "AI_GENERATED_PREDICTIONS")) {
            warnings.add(issue(
                    "optional_section_empty",
                    "Optional section AI_GENERATED_PREDICTIONS is empty or placeholder-only",
                    sourcePath,
                    "AI_GENERATED_PREDICTIONS",
                    -1));
        }

        return missing;
    }

    private boolean isPlaceholderOnlySection(List<MarkdownSection> sections, String sectionKey) {
        return sections.stream()
                .filter(section -> sectionKey.equals(normalizeCanonicalSectionKey(section.canonicalKey())))
                .anyMatch(section -> isBlankOrPlaceholderOnly(section.body()));
    }

    private boolean hasMeaningfulTableContent(List<MarkdownTable> tables, String sectionKey) {
        if (tables == null || sectionKey == null) {
            return false;
        }
        return tables.stream()
                .filter(table -> sectionKey.equals(normalizeCanonicalSectionKey(table.sectionKey())))
                .anyMatch(table -> table.rows().stream()
                        .flatMap(List::stream)
                        .anyMatch(value -> !isBlankOrPlaceholderOnly(value)));
    }

    private String extractFinalVerdict(List<MarkdownSection> sections,
            List<ParsingIssue> warnings,
            List<ParsingIssue> errors,
            String sourcePath) {
        MarkdownSection verdictSection = sections.stream()
                .filter(section -> "FINAL_SELF_VERDICT".equals(normalizeCanonicalSectionKey(section.canonicalKey())))
                .findFirst()
                .orElse(null);
        if (verdictSection == null || isBlankOrPlaceholderOnly(verdictSection.body())) {
            warnings.add(issue(
                    "verdict_missing",
                    "Final self-verdict section is missing or empty",
                    sourcePath,
                    "FINAL_SELF_VERDICT",
                    -1));
            return null;
        }

        String raw = verdictSection.body().trim();
        String direct = normalizeVerdict(raw);
        if (direct != null) {
            return direct;
        }

        for (String line : raw.split("\\R")) {
            String candidate = line.trim();
            if (candidate.startsWith("-")) {
                candidate = candidate.substring(1).trim();
            }
            if (candidate.startsWith("*")) {
                candidate = candidate.substring(1).trim();
            }
            Matcher matcher = VERDICT_PATTERN.matcher(candidate);
            if (matcher.matches()) {
                return matcher.group(1).toUpperCase(Locale.ROOT);
            }
        }

        errors.add(new ParsingIssue(
                "verdict_invalid",
                "error",
                "Final self-verdict must be one of PASS, NEEDS_UPDATE, or BLOCKED",
                sourcePath,
                "FINAL_SELF_VERDICT",
                verdictSection.startLine()));
        return null;
    }

    private String normalizeVerdict(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT).replaceAll("\\s+", "_");
        if ("PASS".equals(upper) || "NEEDS_UPDATE".equals(upper) || "BLOCKED".equals(upper)) {
            return upper;
        }
        return null;
    }

    private void checkSectionOrder(List<MarkdownSection> sections,
            List<ParsingIssue> warnings,
            String sourcePath) {
        List<String> actual = sections.stream()
                .filter(section -> section.level() >= 2)
                .map(section -> normalizeCanonicalSectionKey(section.canonicalKey()))
                .filter(this::isCanonicalSection)
                .toList();
        List<String> expected = CANONICAL_SECTION_KEYS.stream()
                .filter(key -> !OPTIONAL_SECTION_KEYS.contains(key))
                .toList();

        int lastSeen = -1;
        boolean orderBroken = false;
        for (String key : actual) {
            int idx = expected.indexOf(key);
            if (idx < 0) {
                continue;
            }
            if (idx < lastSeen) {
                orderBroken = true;
                break;
            }
            lastSeen = idx;
        }

        if (orderBroken) {
            warnings.add(issue(
                    "section_order_deviation",
                    "Canonical section order differs from the template order",
                    sourcePath,
                    null,
                    -1));
        }
    }

    private void checkNestedDepth(List<MarkdownSection> sections,
            List<ParsingIssue> warnings,
            String sourcePath) {
        for (MarkdownSection section : sections) {
            if (section.level() > 2) {
                warnings.add(issue(
                        "nested_subsection_depth_exceeded",
                        "Nested subsection depth exceeds one level under the canonical heading",
                        sourcePath,
                        section.canonicalKey(),
                        section.startLine()));
            }
        }
    }

    private Map<String, Object> buildParsedSummary(String ticketId,
            String parseMode,
            String parseStatus,
            String artifactStatus,
            String contentHash,
            List<MarkdownSection> sections,
            List<MarkdownTable> tables,
            String finalVerdict,
            List<ParsingIssue> warnings,
            List<ParsingIssue> errors,
            List<String> requiredSectionsMissing,
            List<MarkdownPlaceholder> placeholders) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ticket_id", ticketId);
        summary.put("parse_mode", parseMode);
        summary.put("parse_status", parseStatus);
        summary.put("artifact_status", artifactStatus);
        summary.put("content_hash", contentHash);
        summary.put("parser_version", PARSER_VERSION);
        summary.put("section_count", sections.size());
        summary.put("table_count", tables.size());
        summary.put("free_text_section_count",
                sections.stream().filter(section -> FREE_TEXT_SECTION_KEYS.contains(section.canonicalKey())).count());
        summary.put("warning_count", warnings.size());
        summary.put("error_count", errors.size());
        summary.put("placeholder_count", placeholders.size());
        summary.put("required_sections_missing_count", requiredSectionsMissing.size());
        summary.put("has_missing_required_sections", !requiredSectionsMissing.isEmpty());
        summary.put("final_verdict", finalVerdict);
        summary.put("final_verdict_valid", finalVerdict != null);
        summary.put("has_review_checklist", sections.stream().anyMatch(section -> "SELF_CHECK_USING_REVIEW_CHECKLIST"
                .equals(normalizeCanonicalSectionKey(section.canonicalKey()))));
        summary.put("has_human_review", sections.stream().anyMatch(
                section -> "ITEMS_REVIEWED_BY_HUMANS".equals(normalizeCanonicalSectionKey(section.canonicalKey()))));
        return summary;
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

    private String inferTicketId(Map<String, String> frontMatter,
            Map<String, String> headerMetadata,
            String sourcePath,
            List<MarkdownSection> sections) {
        String candidate = firstNonBlank(
                frontMatter.get("ticket_id"),
                frontMatter.get("ticket-id"),
                frontMatter.get("ticketid"),
                headerMetadata.get("ticket_id"),
                headerMetadata.get("ticket-id"),
                headerMetadata.get("ticketid"));
        if (candidate != null && isTicketId(candidate)) {
            return candidate;
        }

        String inferred = inferFromPath(sourcePath);
        if (inferred != null && isTicketId(inferred)) {
            return inferred;
        }

        for (MarkdownSection section : sections) {
            if ("SELF_REVIEW".equals(normalizeCanonicalSectionKey(section.canonicalKey()))) {
                String fromBody = extractTicketIdFromText(section.body());
                if (isTicketId(fromBody)) {
                    return fromBody;
                }
            }
        }

        return candidate != null && !candidate.isBlank() ? candidate.trim() : null;
    }

    private String extractTicketIdFromText(String text) {
        if (text == null) {
            return null;
        }
        for (String line : text.split("\\R")) {
            String cleaned = line.trim()
                    .replace("**", "")
                    .replace("*", "");
            int idx = cleaned.indexOf(':');
            if (idx >= 0) {
                cleaned = cleaned.substring(idx + 1).trim();
            }
            if (isTicketId(cleaned)) {
                return cleaned;
            }
        }
        return null;
    }

    private String inferFromPath(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return null;
        }
        Matcher matcher = SOURCE_PATH_PATTERN.matcher(sourcePath.replace('\\', '/'));
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    private boolean isTicketId(String value) {
        return value != null && TICKET_ID_PATTERN.matcher(value.trim()).matches();
    }

    private String normalizeParseMode(String parseMode) {
        if (parseMode == null || parseMode.isBlank()) {
            return DEFAULT_PARSE_MODE;
        }
        return parseMode.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isCanonicalSection(String key) {
        return key != null && CANONICAL_SECTION_KEYS.contains(key);
    }

    private MarkdownSection normalizeSection(MarkdownSection section) {
        if (section == null) {
            return null;
        }
        String canonicalKey = normalizeCanonicalSectionKey(section.canonicalKey());
        return new MarkdownSection(
                section.title(),
                canonicalKey,
                section.level(),
                section.body(),
                section.startLine(),
                section.endLine());
    }

    private MarkdownTable normalizeTable(MarkdownTable table) {
        if (table == null) {
            return null;
        }
        return new MarkdownTable(
                normalizeCanonicalSectionKey(table.sectionKey()),
                table.sectionTitle(),
                table.headers(),
                table.rows(),
                table.startLine());
    }

    private String normalizeCanonicalSectionKey(String key) {
        if (key == null) {
            return null;
        }
        if ("RUNN_COMMAND_AND_RESULTS".equalsIgnoreCase(key)) {
            return "RUN_COMMAND_AND_RESULTS";
        }
        return key;
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

    private boolean isBlankOrPlaceholderOnly(String value) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        String[] lines = value.split("\\R");
        boolean sawMeaningfulText = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (isPlaceholderToken(trimmed)) {
                continue;
            }
            sawMeaningfulText = true;
            break;
        }
        return !sawMeaningfulText;
    }

    private boolean isPlaceholderToken(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        return List.of("---", "TBD", "TODO", "N/A", "-").stream().anyMatch(token -> token.equalsIgnoreCase(trimmed))
                || trimmed.matches("^<[^>]*>$");
    }

    private List<ParsedExceptionRecord> extractExceptionRecords(List<MarkdownTable> tables,
            List<ParsingIssue> warnings,
            String sourcePath) {
        List<ParsedExceptionRecord> records = new ArrayList<>();
        boolean sectionPresent = false;

        for (MarkdownTable table : tables) {
            if (!"EXCEPTION_RECORD".equals(normalizeCanonicalSectionKey(table.sectionKey()))) {
                continue;
            }
            sectionPresent = true;
            List<String> headers = table.headers();
            int typeIdx = findHeaderIndex(headers, "exception type", "exception_type", "type");
            int reasonIdx = findHeaderIndex(headers, "reason");
            int altIdx = findHeaderIndex(headers, "alternative check", "alternative_check", "alternative");
            int appIdx = findHeaderIndex(headers, "approved");
            int roleIdx = findHeaderIndex(headers, "approved by role", "approved_by_role", "approved by", "role");
            int expiryIdx = findHeaderIndex(headers, "expiry date", "expiry_date", "expiry");
            int followIdx = findHeaderIndex(headers, "follow up status", "follow_up_status", "follow-up status",
                    "follow up", "follow_up");
            int statusIdx = findHeaderIndex(headers, "status");

            for (List<String> row : table.rows()) {
                String type = safeGet(row, typeIdx);
                if (type == null || isBlankOrPlaceholderOnly(type)) {
                    continue;
                }
                String reason = safeGet(row, reasonIdx);
                String truncatedReason = reason != null && reason.length() > 1000
                        ? reason.substring(0, 1000)
                        : reason;
                String followUp = safeGet(row, followIdx);
                records.add(new ParsedExceptionRecord(
                        type.trim(),
                        reason != null && !isBlankOrPlaceholderOnly(reason),
                        truncatedReason,
                        safeGet(row, altIdx),
                        isTruthy(safeGet(row, appIdx)),
                        safeGet(row, roleIdx),
                        safeGet(row, expiryIdx),
                        (followUp != null && !followUp.isBlank()) ? followUp.trim() : "OPEN",
                        (safeGet(row, statusIdx) != null && !safeGet(row, statusIdx).isBlank())
                                ? safeGet(row, statusIdx).trim()
                                : null,
                        null));
            }
        }

        if (sectionPresent && records.isEmpty()) {
            warnings.add(issue(
                    "exception_table_empty",
                    "EXCEPTION_RECORD section present but contains no data rows",
                    sourcePath,
                    "EXCEPTION_RECORD",
                    -1));
        }
        return records;
    }

    private int findHeaderIndex(List<String> headers, String... aliases) {
        if (headers == null) {
            return -1;
        }
        for (String alias : aliases) {
            String normalizedAlias = alias.trim().toLowerCase(Locale.ROOT);
            for (int i = 0; i < headers.size(); i++) {
                String h = headers.get(i);
                if (h != null && h.trim().toLowerCase(Locale.ROOT).equals(normalizedAlias)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String safeGet(List<String> row, int idx) {
        if (row == null || idx < 0 || idx >= row.size()) {
            return null;
        }
        String val = row.get(idx);
        return (val == null || val.isBlank()) ? null : val.trim();
    }

    private static boolean isTruthy(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return "yes".equals(v) || "true".equals(v) || "y".equals(v) || "1".equals(v);
    }

    private ParsingIssue issue(String code,
            String message,
            String sourcePath,
            String sectionKey,
            int line) {
        return new ParsingIssue(code, "warning", message, sourcePath, sectionKey, line);
    }

    public record ParsedExceptionRecord(
            String exceptionType,
            boolean reasonPresent,
            String reason,
            String alternativeCheck,
            boolean approved,
            String approvedByRoleName,
            String expiryDate,
            String followUpStatus,
            String status,
            String sourceSection) {
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
            List<MarkdownSection> sections,
            List<MarkdownSection> freeTextSections,
            List<MarkdownTable> tables,
            List<MarkdownTable> tableSections,
            List<MarkdownPlaceholder> placeholders,
            List<ParsingIssue> warnings,
            List<ParsingIssue> errors,
            List<String> requiredSectionsMissing,
            String finalVerdict,
            String normalizedContent,
            String contentHash,
            String parserVersion,
            Map<String, Object> parsedSummary,
            List<ParsedExceptionRecord> exceptionRecords) {
        public boolean containsAnywhere(String needle) {
            if (needle == null || needle.isBlank()) {
                return false;
            }
            String low = needle.toLowerCase(Locale.ROOT);
            return sections.stream().anyMatch(v -> v.body() != null && v.body().toLowerCase(Locale.ROOT).contains(low))
                    || tables.stream().anyMatch(table -> table.rows().stream().flatMap(List::stream)
                            .anyMatch(v -> v != null && v.toLowerCase(Locale.ROOT).contains(low)));
        }
    }
}
