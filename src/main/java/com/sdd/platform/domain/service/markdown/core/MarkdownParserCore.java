package com.sdd.platform.domain.service.markdown.core;

import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Generic Markdown parser core shared by document adapters.
 *
 * Responsibilities:
 * - normalize Markdown text
 * - read YAML front matter
 * - extract headings/sections
 * - extract Markdown tables
 * - detect placeholders
 * - standardize warnings/errors
 * - calculate content hash
 */
public class MarkdownParserCore {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern TOP_META_PATTERN = Pattern.compile("^\\*\\*([^*]+)\\*\\*:\\s*(.+?)\\s*$");
    private static final Pattern PLACEHOLDER_BRACKET_PATTERN = Pattern.compile("^<[^>]*>$");
    private static final Pattern TRAILING_NUMBERED_HEADING_PATTERN = Pattern.compile("^\\d+(?:\\.\\d+)*\\.\\s+");
    private static final List<String> PLACEHOLDER_TOKENS = List.of("---", "TBD", "TODO", "N/A", "-");

    private final Parser parser;

    public MarkdownParserCore() {
        this.parser = Parser.builder()
                .extensions(List.of(YamlFrontMatterExtension.create()))
                .build();
    }

    public MarkdownDocument parse(String content) {
        return parse(content, null);
    }

    public MarkdownDocument parse(String content, String sourcePath) {
        String normalized = normalizeContent(content);
        Map<String, String> frontMatter = extractFrontMatter(normalized);
        Map<String, String> headerMetadata = extractHeaderMetadata(normalized);
        List<MarkdownSection> sections = extractSections(normalized);
        List<MarkdownTable> tables = extractTables(sections);
        List<MarkdownPlaceholder> placeholders = detectPlaceholders(sections, tables);
        List<MarkdownIssue> warnings = new ArrayList<>();
        List<MarkdownIssue> errors = new ArrayList<>();

        if (normalized.isBlank()) {
            errors.add(issue("markdown_empty", "Markdown content is empty", "error", sourcePath, null, -1));
        }

        if (sections.isEmpty() && !normalized.isBlank()) {
            warnings.add(issue("section_missing", "No Markdown headings were detected", "warning", sourcePath, null, -1));
        }

        if (!frontMatter.isEmpty() && frontMatter.keySet().stream().anyMatch(key -> key.isBlank())) {
            warnings.add(issue("front_matter_invalid", "Front matter contains blank keys", "warning", sourcePath, null, -1));
        }

        String hash = sha256(normalized);
        return new MarkdownDocument(
                sourcePath,
                normalized,
                hash,
                Collections.unmodifiableMap(frontMatter),
                Collections.unmodifiableMap(headerMetadata),
                List.copyOf(sections),
                List.copyOf(tables),
                List.copyOf(placeholders),
                List.copyOf(warnings),
                List.copyOf(errors)
        );
    }

    private Map<String, String> extractFrontMatter(String content) {
        Node document = parser.parse(content);
        YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
        document.accept(visitor);

        Map<String, String> frontMatter = new LinkedHashMap<>();
        visitor.getData().forEach((key, values) -> frontMatter.put(normalizeMetadataKey(key), String.join(", ", values)));
        return frontMatter;
    }

    private Map<String, String> extractHeaderMetadata(String content) {
        Map<String, String> metadata = new LinkedHashMap<>();
        String[] lines = content.split("\\R", -1);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("## ")) {
                break;
            }
            Matcher matcher = TOP_META_PATTERN.matcher(trimmed);
            if (matcher.matches()) {
                metadata.put(normalizeMetadataKey(matcher.group(1)), matcher.group(2).trim());
            }
        }
        return metadata;
    }

    private List<MarkdownSection> extractSections(String content) {
        List<MarkdownSection> sections = new ArrayList<>();
        String[] lines = content.split("\\R", -1);
        SectionBuilder current = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher matcher = HEADING_PATTERN.matcher(line);
            if (matcher.matches()) {
                if (current != null) {
                    current.endLine = i;
                    sections.add(current.build());
                }

                String title = stripNumericPrefix(matcher.group(2).trim());
                current = new SectionBuilder(
                        title,
                        canonicalSectionKey(title),
                        matcher.group(1).length(),
                        i + 1
                );
            } else if (current != null) {
                current.body.append(line).append('\n');
            }
        }

        if (current != null) {
            current.endLine = lines.length;
            sections.add(current.build());
        }

        return sections;
    }

    private List<MarkdownTable> extractTables(List<MarkdownSection> sections) {
        List<MarkdownTable> tables = new ArrayList<>();
        for (MarkdownSection section : sections) {
            tables.addAll(extractTablesFromSection(section));
        }
        return tables;
    }

    private List<MarkdownTable> extractTablesFromSection(MarkdownSection section) {
        List<MarkdownTable> tables = new ArrayList<>();
        String[] lines = section.body().split("\\R", -1);
        int index = 0;

        while (index < lines.length) {
            if (isTableHeaderLine(lines[index]) && index + 1 < lines.length && isTableSeparatorLine(lines[index + 1])) {
                int startLine = section.startLine() + index;
                List<String> rawRows = new ArrayList<>();
                rawRows.add(lines[index]);
                rawRows.add(lines[index + 1]);
                index += 2;
                while (index < lines.length && isTableRowLine(lines[index])) {
                    rawRows.add(lines[index]);
                    index++;
                }
                tables.add(parseTable(section, rawRows, startLine));
                continue;
            }
            index++;
        }

        return tables;
    }

    private MarkdownTable parseTable(MarkdownSection section, List<String> rawRows, int startLine) {
        List<List<String>> rows = rawRows.stream()
                .filter(row -> !isTableSeparatorLine(row))
                .map(this::splitTableRow)
                .toList();

        List<String> headers = rows.isEmpty() ? List.of() : List.copyOf(rows.get(0));
        List<List<String>> dataRows = rows.size() <= 1 ? List.of() : rows.subList(1, rows.size());
        return new MarkdownTable(
                section.canonicalKey(),
                section.title(),
                headers,
                dataRows.stream()
                        .map(List::copyOf)
                        .collect(Collectors.toUnmodifiableList()),
                startLine
        );
    }

    private List<MarkdownPlaceholder> detectPlaceholders(List<MarkdownSection> sections,
                                                         List<MarkdownTable> tables) {
        List<MarkdownPlaceholder> placeholders = new ArrayList<>();

        for (MarkdownSection section : sections) {
            String[] lines = section.body().split("\\R", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (isPlaceholderToken(line)) {
                    placeholders.add(new MarkdownPlaceholder(
                            line,
                            "section",
                            section.canonicalKey(),
                            section.startLine() + i,
                            true
                    ));
                }
            }
        }

        for (MarkdownTable table : tables) {
            for (int rowIndex = 0; rowIndex < table.rows().size(); rowIndex++) {
                List<String> row = table.rows().get(rowIndex);
                for (int colIndex = 0; colIndex < row.size(); colIndex++) {
                    String value = row.get(colIndex);
                    if (isPlaceholderToken(value)) {
                        String columnName = colIndex < table.headers().size()
                                ? table.headers().get(colIndex)
                                : "column-" + (colIndex + 1);
                        placeholders.add(new MarkdownPlaceholder(
                                value,
                                "table:" + columnName,
                                table.sectionKey(),
                                table.startLine() + rowIndex + 1,
                                true
                        ));
                    }
                }
            }
        }

        return placeholders;
    }

    private boolean isTableHeaderLine(String line) {
        return line != null && line.contains("|") && !line.trim().isBlank();
    }

    private boolean isTableSeparatorLine(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = stripEdgePipes(line.trim());
        if (!trimmed.contains("|")) {
            return false;
        }
        String[] cells = trimmed.split("\\|", -1);
        if (cells.length < 2) {
            return false;
        }
        for (String cell : cells) {
            String value = cell.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (!value.matches(":?-{3,}:?")) {
                return false;
            }
        }
        return true;
    }

    private boolean isTableRowLine(String line) {
        return line != null && line.contains("|") && !line.trim().isBlank();
    }

    private List<String> splitTableRow(String line) {
        String trimmed = stripEdgePipes(line.trim());
        return Arrays.stream(trimmed.split("\\|", -1))
                .map(String::trim)
                .toList();
    }

    private boolean isPlaceholderToken(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        if (PLACEHOLDER_TOKENS.stream().anyMatch(token -> token.equalsIgnoreCase(trimmed))) {
            return true;
        }
        return PLACEHOLDER_BRACKET_PATTERN.matcher(trimmed).matches();
    }

    private String normalizeMetadataKey(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
    }

    private String normalizeContent(String content) {
        String normalized = content == null ? "" : content;
        if (normalized.startsWith("\uFEFF")) {
            normalized = normalized.substring(1);
        }
        normalized = normalized.replace("\r\n", "\n").replace('\r', '\n');

        String[] lines = normalized.split("\n", -1);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            builder.append(rtrim(lines[i]));
            if (i < lines.length - 1) {
                builder.append('\n');
            }
        }
        return trimTrailingBlankLines(builder.toString());
    }

    private String trimTrailingBlankLines(String value) {
        int end = value.length();
        while (end > 0) {
            int previousNewLine = value.lastIndexOf('\n', end - 1);
            String tail = value.substring(previousNewLine + 1, end);
            if (!tail.trim().isEmpty()) {
                break;
            }
            end = previousNewLine >= 0 ? previousNewLine : 0;
            if (end == 0) {
                break;
            }
        }
        return value.substring(0, Math.max(end, 0));
    }

    private String rtrim(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private String stripNumericPrefix(String value) {
        return TRAILING_NUMBERED_HEADING_PATTERN.matcher(value).replaceFirst("");
    }

    private String canonicalSectionKey(String rawHeading) {
        String normalized = rawHeading == null ? "" : rawHeading
                .replace('–', '-')
                .replace('—', '-')
                .replace('/', ' ')
                .replace('-', ' ')
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit} ]+", " ")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();

        return switch (normalized) {
            case "context purpose" -> "CONTEXT_PURPOSE";
            case "scope" -> "SCOPE";
            case "within range" -> "SCOPE_WITHIN_RANGE";
            case "out of range" -> "SCOPE_OUT_OF_RANGE";
            case "terminology" -> "TERMINOLOGY";
            case "as is" -> "AS_IS";
            case "to be" -> "TO_BE";
            case "detailed specification" -> "DETAILED_SPECIFICATION";
            case "business rules" -> "BUSINESS_RULES";
            case "input" -> "INPUT";
            case "output" -> "OUTPUT";
            case "error exception" -> "ERROR_EXCEPTION";
            case "boundary value" -> "BOUNDARY_VALUE";
            case "non functional" -> "NON_FUNCTIONAL";
            case "acceptance criteria" -> "ACCEPTANCE_CRITERIA";
            case "examples" -> "EXAMPLES";
            case "normal case" -> "EXAMPLE_NORMAL_CASE";
            case "error case" -> "EXAMPLE_ERROR_CASE";
            case "boundary case" -> "EXAMPLE_BOUNDARY_CASE";
            case "source availability summary" -> "SOURCE_AVAILABILITY_SUMMARY";
            case "complexity classification" -> "COMPLEXITY_CLASSIFICATION";
            case "fe be contract impact" -> "FE_BE_CONTRACT_IMPACT";
            case "db migration impact" -> "DB_MIGRATION_IMPACT";
            case "security privacy impact" -> "SECURITY_PRIVACY_IMPACT";
            case "operation maintenance impact" -> "OPERATION_MAINTENANCE_IMPACT";
            case "test strategy summary" -> "TEST_STRATEGY_SUMMARY";
            case "human decision required" -> "HUMAN_DECISION_REQUIRED";
            case "assumptions and inference log" -> "ASSUMPTIONS_INFERENCE_LOG";
            case "open issues" -> "OPEN_ISSUES";
            default -> normalized.isBlank()
                    ? "UNKNOWN_SECTION"
                    : normalized.toUpperCase(Locale.ROOT).replace(' ', '_');
        };
    }

    private String stripEdgePipes(String value) {
        String result = value;
        if (result.startsWith("|")) {
            result = result.substring(1);
        }
        if (result.endsWith("|")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private MarkdownIssue issue(String code,
                                String message,
                                String severity,
                                String path,
                                String sectionKey,
                                int line) {
        return new MarkdownIssue(code, message, severity, path, sectionKey, line);
    }

    private String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to calculate content hash", ex);
        }
    }

    private static final class SectionBuilder {
        private final String title;
        private final String canonicalKey;
        private final int level;
        private final int startLine;
        private final StringBuilder body = new StringBuilder();
        private int endLine;

        private SectionBuilder(String title, String canonicalKey, int level, int startLine) {
            this.title = title;
            this.canonicalKey = canonicalKey;
            this.level = level;
            this.startLine = startLine;
        }

        private MarkdownSection build() {
            return new MarkdownSection(title, canonicalKey, level, body.toString().trim(), startLine, endLine);
        }
    }

    public record MarkdownDocument(
            String sourcePath,
            String normalizedContent,
            String contentHash,
            Map<String, String> frontMatter,
            Map<String, String> headerMetadata,
            List<MarkdownSection> sections,
            List<MarkdownTable> tables,
            List<MarkdownPlaceholder> placeholders,
            List<MarkdownIssue> warnings,
            List<MarkdownIssue> errors
    ) {
        public Map<String, String> sectionMap() {
            return sections.stream().collect(Collectors.toMap(
                    MarkdownSection::canonicalKey,
                    MarkdownSection::body,
                    (left, right) -> left,
                    LinkedHashMap::new
            ));
        }
    }

    public record MarkdownSection(
            String title,
            String canonicalKey,
            int level,
            String body,
            int startLine,
            int endLine
    ) {}

    public record MarkdownTable(
            String sectionKey,
            String sectionTitle,
            List<String> headers,
            List<List<String>> rows,
            int startLine
    ) {}

    public record MarkdownPlaceholder(
            String value,
            String location,
            String sectionKey,
            int line,
            boolean required
    ) {}

    public record MarkdownIssue(
            String code,
            String message,
            String severity,
            String path,
            String sectionKey,
            int line
    ) {
        public boolean isError() {
            return "error".equalsIgnoreCase(severity);
        }
    }
}
