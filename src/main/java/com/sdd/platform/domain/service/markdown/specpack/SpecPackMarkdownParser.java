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

    private static final Pattern AC_PATTERN = Pattern.compile("^AC-(?<ticket>[A-Z0-9][A-Z0-9-]*)-(?<index>\\d+)$");
    private static final Pattern SOURCE_PATH_PATTERN = Pattern.compile("(?i)(?:^|.*/)changes/([^/\\\\]+)/spec-pack\\.md$");
    private static final String DEFAULT_PARSE_MODE = "draft";
    private static final String PARSER_VERSION = "markdown-core-v1";

    private static final List<String> REQUIRED_SECTION_KEYS = List.of(
            "CONTEXT_PURPOSE",
            "SCOPE",
            "SCOPE_WITHIN_RANGE",
            "SCOPE_OUT_OF_RANGE",
            "TERMINOLOGY",
            "AS_IS",
            "TO_BE",
            "DETAILED_SPECIFICATION",
            "BUSINESS_RULES",
            "INPUT",
            "OUTPUT",
            "ERROR_EXCEPTION",
            "BOUNDARY_VALUE",
            "NON_FUNCTIONAL",
            "ACCEPTANCE_CRITERIA",
            "EXAMPLES",
            "EXAMPLE_NORMAL_CASE",
            "EXAMPLE_ERROR_CASE",
            "EXAMPLE_BOUNDARY_CASE",
            "SOURCE_AVAILABILITY_SUMMARY",
            "COMPLEXITY_CLASSIFICATION",
            "FE_BE_CONTRACT_IMPACT",
            "DB_MIGRATION_IMPACT",
            "SECURITY_PRIVACY_IMPACT",
            "OPERATION_MAINTENANCE_IMPACT",
            "TEST_STRATEGY_SUMMARY",
            "HUMAN_DECISION_REQUIRED",
            "ASSUMPTIONS_INFERENCE_LOG",
            "OPEN_ISSUES"
    );

    // Parent section → list of required child sections.
    // If a parent has children, parent content is NOT checked; only children are checked.
    // SCOPE is treated as present as soon as the heading exists.
    private static final Map<String, List<String>> PARENT_CHILD_HIERARCHY = Map.ofEntries(
            Map.entry("SCOPE", List.of("SCOPE_WITHIN_RANGE", "SCOPE_OUT_OF_RANGE")),
            Map.entry("DETAILED_SPECIFICATION", List.of("BUSINESS_RULES", "INPUT", "OUTPUT", "ERROR_EXCEPTION", "BOUNDARY_VALUE", "NON_FUNCTIONAL")),
            Map.entry("EXAMPLES", List.of("EXAMPLE_NORMAL_CASE", "EXAMPLE_ERROR_CASE", "EXAMPLE_BOUNDARY_CASE"))
    );

    private static final Set<String> HEADING_ONLY_SECTION_KEYS = Set.of("SCOPE");

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
                frontMatter.get("ticketid")
        );
        if (rawFrontMatterTicketId != null && !isTicketId(rawFrontMatterTicketId)) {
            warnings.add(new ParsingIssue(
                    "front_matter_ticket_id_invalid",
                    "warning",
                    "Front matter ticket_id is present but invalid: " + rawFrontMatterTicketId,
                    sourcePath,
                    null,
                    -1
            ));
        }

        String ticketId = inferTicketId(frontMatter, headerMetadata, sourcePath, sections);
        if (ticketId == null || ticketId.isBlank()) {
            warnings.add(new ParsingIssue(
                    "ticket_id_missing",
                    "warning",
                    "Unable to infer ticket_id from front matter, header metadata, or source path",
                    sourcePath,
                    null,
                    -1
            ));
        }

        List<AcceptanceCriterion> acceptanceCriteria = extractAcceptanceCriteria(
                ticketId,
                tables,
                warnings,
                sourcePath
        );

        List<String> requiredFieldsMissing = detectRequiredFieldsMissing(frontMatter, sections, sectionList, acceptanceCriteria, ticketId);
        if (!requiredFieldsMissing.isEmpty()) {
            warnings.add(new ParsingIssue(
                    "required_fields_missing",
                    "warning",
                    "Missing required fields: " + String.join(", ", requiredFieldsMissing),
                    sourcePath,
                    null,
                    -1
            ));
        }

        if (!placeholders.isEmpty()) {
            warnings.add(new ParsingIssue(
                    "placeholder_detected",
                    "warning",
                    "Placeholder values were detected in required fields",
                    sourcePath,
                    null,
                    -1
            ));
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
                placeholders
        );

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
                parsedSummary
        );
    }

    public boolean hasSection(ParsedArtifact parsed, String... aliases) {
        if (parsed == null || aliases == null || aliases.length == 0) {
            return false;
        }

        for (String alias : aliases) {
            String normalizedAlias = normalizeSectionAlias(alias);
            if (parsed.sections().keySet().stream().anyMatch(key -> key.equalsIgnoreCase(normalizedAlias) || key.contains(normalizedAlias))) {
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
                        issue.line()
                ))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<AcceptanceCriterion> extractAcceptanceCriteria(String ticketId,
                                                                List<MarkdownTable> tables,
                                                                List<ParsingIssue> warnings,
                                                                String sourcePath) {
        List<AcceptanceCriterion> acceptanceCriteria = new ArrayList<>();
        for (MarkdownTable table : tables) {
            if (!"ACCEPTANCE_CRITERIA".equals(table.sectionKey())) {
                continue;
            }
            if (table.rows().isEmpty()) {
                warnings.add(new ParsingIssue(
                        "ac_table_empty",
                        "warning",
                        "Acceptance Criteria table has no data rows",
                        sourcePath,
                        table.sectionKey(),
                        table.startLine()
                ));
                continue;
            }

            int idIndex = findColumnIndex(table.headers(), "acid", "ac id", "id");
            int descriptionIndex = findColumnIndex(table.headers(), "description", "details");
            int testableIndex = findColumnIndex(table.headers(), "testable", "testable?");
            int notesIndex = findColumnIndex(table.headers(), "notes", "remark", "remarks");

            for (int rowIndex = 0; rowIndex < table.rows().size(); rowIndex++) {
                List<String> row = table.rows().get(rowIndex);
                String rawId = cell(row, idIndex >= 0 ? idIndex : 0);
                String description = cell(row, descriptionIndex >= 0 ? descriptionIndex : 1);
                String testable = cell(row, testableIndex >= 0 ? testableIndex : 2);
                String notes = cell(row, notesIndex >= 0 ? notesIndex : 3);

                boolean validFormat = isValidAcId(rawId, ticketId);
                Integer sequenceNumber = extractSequenceNumber(rawId);
                if (!validFormat) {
                    warnings.add(new ParsingIssue(
                            "ac_format_invalid",
                            "warning",
                            "AC ID does not match the expected AC-<TICKET>-<n> format: " + rawId,
                            sourcePath,
                            table.sectionKey(),
                            table.startLine() + rowIndex + 1
                    ));
                }

                acceptanceCriteria.add(new AcceptanceCriterion(
                        rawId,
                        description,
                        testable,
                        notes,
                        validFormat,
                        sequenceNumber
                ));
            }
        }
        return acceptanceCriteria;
    }

    private boolean isValidAcId(String rawId, String ticketId) {
        if (rawId == null || rawId.isBlank()) {
            return false;
        }
        Matcher matcher = AC_PATTERN.matcher(rawId.trim());
        if (!matcher.matches()) {
            return false;
        }
        if (ticketId == null || ticketId.isBlank()) {
            return true;
        }
        return ticketId.equalsIgnoreCase(matcher.group("ticket"));
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
            // Skip subsections - they are checked under their parent if parent has a hierarchy
            if (PARENT_CHILD_HIERARCHY.values().stream().anyMatch(children -> children.contains(requiredSectionKey))) {
                continue;
            }

            // If this section is a parent with children, check all children
            if (PARENT_CHILD_HIERARCHY.containsKey(requiredSectionKey)) {
                if (HEADING_ONLY_SECTION_KEYS.contains(requiredSectionKey)) {
                    if (!sections.containsKey(requiredSectionKey)) {
                        missing.add("section:" + requiredSectionKey);
                    }
                    continue;
                }
                List<String> children = PARENT_CHILD_HIERARCHY.get(requiredSectionKey);
                boolean anyChildHasContent = false;
                for (String childKey : children) {
                    String childContent = sections.get(childKey);
                    if (childContent != null && !childContent.trim().isEmpty()) {
                        anyChildHasContent = true;
                        break;
                    }
                }
                if (!anyChildHasContent) {
                    missing.add("section:" + requiredSectionKey);
                }
            } else {
                // Leaf section: pass if it has direct content OR has dynamic subsections with content
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
            missing.add("section:ACCEPTANCE_CRITERIA rows");
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
        summary.put("open_issue_open_count", openIssueStats.openCount());
        summary.put("open_issue_closed_count", openIssueStats.closedCount());
        // missing required sections
        summary.put("required_sections_missing_count", requiredSectionsMissingCount);
        summary.put("has_missing_required_sections", requiredSectionsMissingCount > 0);
        // detection flags
        summary.put("has_open_issue_detected", sections.containsKey("OPEN_ISSUES"));
        summary.put("has_risk_detected", sections.containsKey("OPEN_ISSUES"));
        summary.put("has_traceability_detected", sections.containsKey("ASSUMPTIONS_INFERENCE_LOG"));
        return summary;
    }

    private OpenIssueStats countOpenIssueStats(List<MarkdownTable> tables) {
        int totalCount = 0;
        int openCount = 0;
        int closedCount = 0;
        for (MarkdownTable table : tables) {
            if (!"OPEN_ISSUES".equals(table.sectionKey())) {
                continue;
            }
            int statusIndex = findColumnIndex(table.headers(), "status");
            for (List<String> row : table.rows()) {
                totalCount++;
                String status = statusIndex >= 0 ? cell(row, statusIndex) : "";
                if ("open".equalsIgnoreCase(status)) {
                    openCount++;
                } else if ("closed".equalsIgnoreCase(status)) {
                    closedCount++;
                }
            }
        }
        return new OpenIssueStats(totalCount, openCount, closedCount);
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
                frontMatter.get("ticketid")
        );
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
                headerMetadata.get("ticketid")
        );
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
                if (normalizedHeaders.get(i).equals(normalizedAlias) || normalizedHeaders.get(i).contains(normalizedAlias)) {
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
            int line
    ) {}

    public record AcceptanceCriterion(
            String id,
            String description,
            String testable,
            String notes,
            boolean validFormat,
            Integer sequenceNumber
    ) {}

    public record OpenIssueStats(
            int totalCount,
            int openCount,
            int closedCount
    ) {}

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
            Map<String, Object> parsedSummary
    ) {
        public boolean containsAnywhere(String needle) {
            if (needle == null || needle.isBlank()) {
                return false;
            }
            String low = needle.toLowerCase(Locale.ROOT);
            return sections.values().stream().anyMatch(v -> v != null && v.toLowerCase(Locale.ROOT).contains(low))
                    || tables.stream().anyMatch(table -> table.rows().stream().flatMap(List::stream).anyMatch(v -> v != null && v.toLowerCase(Locale.ROOT).contains(low)));
        }
    }
}
