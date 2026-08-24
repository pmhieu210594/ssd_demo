package com.sdd.platform.domain.service;

import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parses an SDD evidence Markdown file into structured pieces:
 *   - YAML front matter (if present)
 *   - Headings → section bodies
 *   - Numbered AC lines (e.g. "- AC-1: ...")
 *   - sha256 content hash
 *
 * Pure domain logic — no Spring, no persistence. Wired as a Spring bean by
 * {@code config/DomainConfig}.
 */
public class ArtifactNormalizer {

    private static final Pattern AC_LINE = Pattern.compile("(?m)^[-*]\\s+(AC-\\d+)\\s*[:：]\\s*(.+)$");

    private final Parser parser;

    public ArtifactNormalizer() {
        this.parser = Parser.builder()
                .extensions(List.of(YamlFrontMatterExtension.create()))
                .build();
    }

    public ParsedArtifact parse(String content) {
        if (content == null) content = "";

        Node document = parser.parse(content);

        YamlFrontMatterVisitor fm = new YamlFrontMatterVisitor();
        document.accept(fm);
        Map<String, List<String>> rawFrontMatter = fm.getData();
        Map<String, String> frontMatter = new LinkedHashMap<>();
        rawFrontMatter.forEach((k, v) -> frontMatter.put(k, String.join(",", v)));

        Map<String, String> sections = extractSections(content);
        List<AcceptanceLine> acLines = extractAcLines(content);
        String hash = sha256(content);

        return new ParsedArtifact(frontMatter, sections, acLines, hash);
    }

    /**
     * Split markdown by H2/H3 headings. Header text becomes the key (lowercased,
     * whitespace-normalized) — anything until the next heading of the same or
     * higher level is the value.
     */
    private Map<String, String> extractSections(String content) {
        Map<String, String> sections = new LinkedHashMap<>();
        String[] lines = content.split("\\R", -1);
        String currentKey = null;
        StringBuilder buf = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("## ") || line.startsWith("### ")) {
                if (currentKey != null) {
                    sections.put(currentKey, buf.toString().trim());
                }
                currentKey = normalizeHeading(line.replaceFirst("^#+\\s+", ""));
                buf.setLength(0);
            } else if (currentKey != null) {
                buf.append(line).append('\n');
            }
        }
        if (currentKey != null) {
            sections.put(currentKey, buf.toString().trim());
        }
        return sections;
    }

    private String normalizeHeading(String raw) {
        return raw.toLowerCase().trim()
                .replaceFirst("^\\d+[\\p{Punct}\\s]*", "")
                .replaceAll("[／/]", "-")
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private List<AcceptanceLine> extractAcLines(String content) {
        List<AcceptanceLine> result = new ArrayList<>();
        var matcher = AC_LINE.matcher(content);
        while (matcher.find()) {
            result.add(new AcceptanceLine(matcher.group(1), matcher.group(2).trim()));
        }
        return result;
    }

    private String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "";
        }
    }

    public boolean hasSection(ParsedArtifact parsed, String... aliases) {
        for (String alias : aliases) {
            if (parsed.sections().keySet().stream()
                    .anyMatch(k -> k.contains(alias.toLowerCase()))) {
                return true;
            }
        }
        return false;
    }

    public record AcceptanceLine(String number, String description) {}

    public record ParsedArtifact(
            Map<String, String> frontMatter,
            Map<String, String> sections,
            List<AcceptanceLine> acceptanceCriteria,
            String contentHash
    ) {
        public boolean containsAnywhere(String needle) {
            String low = needle.toLowerCase();
            return sections.values().stream().anyMatch(v -> v.toLowerCase().contains(low));
        }
    }
}
