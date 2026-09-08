package com.sdd.platform.domain.service.markdown.specpack;

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

/**
 * Dedicated parser for `spec-pack.md`.
 *
 * The reusable Markdown parsing work lives in
 * {@link com.sdd.platform.domain.service.markdown.core.MarkdownParserCore}.
 * This parser specializes the shared document into the spec-pack output
 * envelope used by the demo endpoint and later downstream wiring.
 */
public class SpecPackMarkdownParser {

    private static final Pattern AC_PATTERN = Pattern.compile(
            "(?i)^AC-(?<group>\\p{L}[\\p{L}0-9]*(?:-\\p{L}[\\p{L}0-9]*)*)-(?<index>\\d+)(?:/v(?<version>\\d+))?$");
    private static final Pattern SOURCE_PATH_PATTERN = Pattern
            .compile("(?i)(?:^|.*/)changes/([^/\\\\]+)/spec-pack\\.md$");
    private static final String DEFAULT_PARSE_MODE = "draft";
    private static final String PARSER_VERSION = "markdown-core-v1";

    private static final List<String> REQUIRED_SECTION_KEYS = List.of(
            "BỐI_CẢNH_MỤC_ĐÍCH",
            "PHẠM_VI",
            "TRONG_PHẠM_VI",
            "NGOÀI_PHẠM_VI",
            "THUẬT_NGỮ",
            "HIỆN_TRẠNG_TRẠNG_THÁI_MỤC_TIÊU",
            "CHI_TIẾT_ĐẶC_TẢ",
            "YÊU_CẦU_PHI_CHỨC_NĂNG",
            "TIÊU_CHÍ_CHẤP_NHẬN",
            "CÁC_VẤN_ĐỀ_MỞ",
            "RỦI_RO",
            "BẢNG_TRUY_VẾT",
            "PHÁN_ĐỊNH_IMPLEMENTATION_READINESS",
            "THỨ_TỰ_ƯU_TIÊN_OPEN_ISSUES_CẦN_CON_NGƯỜI_QUYẾT_ĐỊNH");

    public static List<String> requiredSectionKeys() {
        return List.copyOf(REQUIRED_SECTION_KEYS);
    }

    public static boolean isParentSection(String sectionKey) {
        return PARENT_CHILD_HIERARCHY.containsKey(sectionKey);
    }

    // Parent section → list of required child sections.
    // If a parent has children, the existence of the parent section itself is
    // sufficient; child content is not required for presence detection.
    // SCOPE is treated as present as soon as the heading exists.
    private static final Map<String, List<String>> PARENT_CHILD_HIERARCHY = Map.ofEntries(
            Map.entry("PHẠM_VI", List.of("TRONG_PHẠM_VI", "NGOÀI_PHẠM_VI")),
            Map.entry("HIỆN_TRẠNG_TRẠNG_THÁI_MỤC_TIÊU", List.of("CƠ_CHẾ_HIỆN_TẠI", "CẢI_TIẾN_THÊM_LẦN_NÀY"))
        );

    private final MarkdownParserCore core;

    public SpecPackMarkdownParser() {
        this(new MarkdownParserCore());
    }

    protected SpecPackMarkdownParser(MarkdownParserCore core) {
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
        Map<String, String> sections = document.sectionMap();
        List<MarkdownSection> sectionList = List.copyOf(document.sections());
        List<MarkdownTable> tables = List.copyOf(document.tables());

        String rawFrontMatterTicketId = firstNonBlank(
                frontMatter.get("ticket_id"),
                frontMatter.get("ticket-id"),
                frontMatter.get("ticketid"));
        if (rawFrontMatterTicketId != null && !isTicketId(rawFrontMatterTicketId)) {
            warnings.add(new ParsingIssue(
                    "front_matter_ticket_id_invalid",
                    "warning",
                    "Front matter ticket_id is present but invalid: " + rawFrontMatterTicketId,
                    sourcePath,
                    null,
                    -1));
        }

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

        List<AcceptanceCriterion> acceptanceCriteria = extractAcceptanceCriteria(
                sectionList,
                tables,
                warnings,
                sourcePath);

        List<String> requiredFieldsMissing = detectRequiredFieldsMissing(frontMatter, sections, sectionList,
                acceptanceCriteria, ticketId);
        if (!requiredFieldsMissing.isEmpty()) {
            warnings.add(new ParsingIssue(
                    "required_fields_missing",
                    "warning",
                    "Missing required fields: " + String.join(", ", requiredFieldsMissing),
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

        boolean artifactExists = !document.normalizedContent().isBlank();
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
                acceptanceCriteria,
                countOpenIssueStats(tables),
                warnings,
                errors,
                requiredFieldsMissing,
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
                acceptanceCriteria,
                placeholders,
                warnings,
                errors,
                requiredFieldsMissing,
                document.normalizedContent(),
                document.contentHash(),
                PARSER_VERSION,
                parsedSummary);
    }

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

    private List<AcceptanceCriterion> extractAcceptanceCriteria(List<MarkdownSection> sectionList,
            List<MarkdownTable> tables,
            List<ParsingIssue> warnings,
            String sourcePath) {
        List<AcceptanceCriterion> acceptanceCriteria = new ArrayList<>();

        Set<String> acGroupSectionKeys = findDynamicChildren(sectionList, "TIÊU_CHÍ_CHẤP_NHẬN").stream()
                .map(MarkdownSection::canonicalKey)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        for (MarkdownTable table : tables) {
            if (!acGroupSectionKeys.contains(table.sectionKey())) {
                continue;
            }
            if (table.rows().isEmpty()) {
                warnings.add(new ParsingIssue(
                        "ac_table_empty",
                        "warning",
                        "Acceptance Criteria table has no data rows",
                        sourcePath,
                        table.sectionKey(),
                        table.startLine()));
                continue;
            }

            int idIndex = findColumnIndex(table.headers(), "acid", "ac id", "id");
            int descriptionIndex = findColumnIndex(table.headers(), "mô tả", "mo ta", "description", "details");
            int unitTestIndex = findColumnIndex(table.headers(), "ut");
            int integrationTestIndex = findColumnIndex(table.headers(), "it");
            int e2eTestIndex = findColumnIndex(table.headers(), "e2e");
            int blackBoxTestIndex = findColumnIndex(table.headers(), "bb");

            for (int rowIndex = 0; rowIndex < table.rows().size(); rowIndex++) {
                List<String> row = table.rows().get(rowIndex);
                String rawId = cell(row, idIndex >= 0 ? idIndex : 0);
                String description = cell(row, descriptionIndex >= 0 ? descriptionIndex : 1);
                boolean unitTest = !cell(row, unitTestIndex).isBlank();
                boolean integrationTest = !cell(row, integrationTestIndex).isBlank();
                boolean e2eTest = !cell(row, e2eTestIndex).isBlank();
                boolean blackBoxTest = !cell(row, blackBoxTestIndex).isBlank();

                boolean validFormat = isValidAcId(rawId);
                Integer sequenceNumber = extractSequenceNumber(rawId);
                if (!validFormat) {
                    warnings.add(new ParsingIssue(
                            "ac_format_invalid",
                            "warning",
                            "AC ID does not match the expected AC-<GROUP>-<n>[/v<version>] format: " + rawId,
                            sourcePath,
                            table.sectionKey(),
                            table.startLine() + rowIndex + 1));
                }

                acceptanceCriteria.add(new AcceptanceCriterion(
                        rawId,
                        description,
                        unitTest,
                        integrationTest,
                        e2eTest,
                        blackBoxTest,
                        validFormat,
                        sequenceNumber));
            }
        }
        return acceptanceCriteria;
    }

    private boolean isValidAcId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return false;
        }
        return AC_PATTERN.matcher(rawId.trim()).matches();
    }

    private Integer extractSequenceNumber(String rawId) {
        if (rawId == null) {
            return null;
        }
        Matcher matcher = AC_PATTERN.matcher(rawId.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group("index"));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<String> detectRequiredFieldsMissing(Map<String, String> frontMatter,
            Map<String, String> sections,
            List<MarkdownSection> sectionList,
            List<AcceptanceCriterion> acceptanceCriteria,
            String ticketId) {
        List<String> missing = new ArrayList<>();

        if (ticketId == null || ticketId.isBlank()) {
            missing.add("ticket_id");
        }

        for (String requiredSectionKey : REQUIRED_SECTION_KEYS) {
            // Skip subsections - they are checked under their parent if parent has a
            // hierarchy
            if (PARENT_CHILD_HIERARCHY.values().stream().anyMatch(children -> children.contains(requiredSectionKey))) {
                continue;
            }

            // If this section is a parent with children, check all children
            if (PARENT_CHILD_HIERARCHY.containsKey(requiredSectionKey)) {
                if (!sections.containsKey(requiredSectionKey)) {
                    missing.add("section:" + requiredSectionKey);
                }
            } else {
                // Leaf section: pass if it has direct content OR has dynamic subsections with
                // content
                List<MarkdownSection> dynamicChildren = findDynamicChildren(sectionList, requiredSectionKey);
                if (!dynamicChildren.isEmpty()) {
                    for (MarkdownSection child : dynamicChildren) {
                        if (child.body() == null || child.body().trim().isEmpty()) {
                            missing.add("section:" + child.canonicalKey());
                        }
                    }
                } else {
                    String sectionContent = sections.get(requiredSectionKey);
                    if (sectionContent == null || sectionContent.trim().isEmpty()) {
                        missing.add("section:" + requiredSectionKey);
                    }
                }
            }
        }

        if (acceptanceCriteria.isEmpty()) {
            missing.add("section:TIÊU_CHÍ_CHẤP_NHẬN rows");
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

    private Map<String, Object> buildParsedSummary(String ticketId,
            String parseMode,
            String parseStatus,
            String artifactStatus,
            String contentHash,
            Map<String, String> sections,
            List<MarkdownTable> tables,
            List<AcceptanceCriterion> acceptanceCriteria,
            OpenIssueStats openIssueStats,
            List<ParsingIssue> warnings,
            List<ParsingIssue> errors,
            List<String> requiredFieldsMissing,
            List<MarkdownPlaceholder> placeholders) {
        int acValidFormatCount = (int) acceptanceCriteria.stream().filter(AcceptanceCriterion::validFormat).count();
        String firstAcId = acceptanceCriteria.isEmpty() ? null : acceptanceCriteria.getFirst().id();
        long requiredSectionsMissingCount = requiredFieldsMissing.stream()
                .filter(f -> f.startsWith("section:")).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        // identity / parse context
        summary.put("ticket_id", ticketId);
        summary.put("parse_mode", parseMode);
        summary.put("parse_status", parseStatus);
        summary.put("artifact_status", artifactStatus);
        summary.put("content_hash", contentHash);
        summary.put("parser_version", PARSER_VERSION);
        // counts
        summary.put("section_count", sections.size());
        summary.put("table_count", tables.size());
        summary.put("ac_count", acceptanceCriteria.size());
        summary.put("ac_valid_format_count", acValidFormatCount);
        summary.put("first_ac_id", firstAcId);
        summary.put("warning_count", warnings.size());
        summary.put("error_count", errors.size());
        summary.put("placeholder_count", placeholders.size());
        summary.put("open_issue_total_count", openIssueStats.totalCount());
        summary.put("open_issue_open_count", openIssueStats.priorityCounts());
        // missing required sections
        summary.put("required_sections_missing_count", requiredSectionsMissingCount);
        summary.put("has_missing_required_sections", requiredSectionsMissingCount > 0);
        // detection flags
        summary.put("has_open_issue_detected", sections.containsKey("CÁC_VẤN_ĐỀ_MỞ"));
        summary.put("has_risk_detected", sections.containsKey("RỦI_RO"));
        summary.put("has_traceability_detected", sections.containsKey("BẢNG_TRUY_VẾT"));
        return summary;
    }

    private OpenIssueStats countOpenIssueStats(List<MarkdownTable> tables) {
        int totalCount = 0;
        Map<String, Integer> priorityCounts = new LinkedHashMap<>();
        for (MarkdownTable table : tables) {
            if (!"CÁC_VẤN_ĐỀ_MỞ".equals(table.sectionKey())) {
                continue;
            }
            int priorityIndex = findColumnIndex(table.headers(), "ưu tiên", "uu tien", "priority");
            for (List<String> row : table.rows()) {
                totalCount++;
                String priority = priorityIndex >= 0 ? cell(row, priorityIndex) : "";
                String priorityKey = priority.isBlank() ? "UNSPECIFIED" : priority.toUpperCase(Locale.ROOT);
                priorityCounts.merge(priorityKey, 1, Integer::sum);
            }
        }
        return new OpenIssueStats(totalCount, priorityCounts);
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

    private String ticketIdFromPath(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return null;
        }
        String normalizedPath = sourcePath.replace('\\', '/');
        Matcher matcher = SOURCE_PATH_PATTERN.matcher(normalizedPath);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
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

    private int findColumnIndex(List<String> headers, String... aliases) {
        if (headers == null || headers.isEmpty()) {
            return -1;
        }
        List<String> normalizedHeaders = headers.stream()
                .map(this::normalizeSectionAlias)
                .toList();
        for (int i = 0; i < normalizedHeaders.size(); i++) {
            for (String alias : aliases) {
                String normalizedAlias = normalizeSectionAlias(alias);
                if (normalizedHeaders.get(i).equals(normalizedAlias)
                        || normalizedHeaders.get(i).contains(normalizedAlias)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String cell(List<String> row, int index) {
        if (row == null || index < 0 || index >= row.size()) {
            return "";
        }
        return row.get(index) == null ? "" : row.get(index).trim();
    }

    public record ParsingIssue(
            String code,
            String severity,
            String message,
            String sourcePath,
            String sectionKey,
            int line) {
    }

    public record AcceptanceCriterion(
            String id,
            String description,
            boolean unitTest,
            boolean integrationTest,
            boolean e2eTest,
            boolean blackBoxTest,
            boolean validFormat,
            Integer sequenceNumber) {
    }

    public record OpenIssueStats(
            int totalCount,
            Map<String, Integer> priorityCounts) {
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
            List<AcceptanceCriterion> acceptanceCriteria,
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
            return sections.values().stream().anyMatch(v -> v != null && v.toLowerCase(Locale.ROOT).contains(low))
                    || tables.stream().anyMatch(table -> table.rows().stream().flatMap(List::stream)
                            .anyMatch(v -> v != null && v.toLowerCase(Locale.ROOT).contains(low)));
        }
    }
}
