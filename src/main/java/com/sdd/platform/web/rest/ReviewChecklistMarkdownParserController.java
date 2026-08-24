package com.sdd.platform.web.rest;

import com.sdd.platform.domain.service.markdown.reviewchecklist.ReviewChecklistMarkdownParser;
import com.sdd.platform.domain.service.markdown.reviewchecklist.ReviewChecklistMarkdownParser.ParsedArtifact;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser endpoint for {@code review-checklist.md}.
 *
 * The file endpoint is intentionally restricted to docs/changes/<TICKET>/review-checklist.md
 * so it cannot be used as a generic file-read primitive.
 */
@RestController
@RequestMapping("/api/v1/markdown-parser/review-checklist")
public class ReviewChecklistMarkdownParserController {

    private static final List<String> ALLOWED_EXTENSIONS = List.of(".md", ".markdown");

    private final ReviewChecklistMarkdownParser parser;

    public ReviewChecklistMarkdownParserController(ReviewChecklistMarkdownParser parser) {
        this.parser = parser;
    }

    @PostMapping("/parse-markdown")
    public Map<String, Object> parseInline(@RequestBody ParseRequest req) {
        ParsedArtifact parsed = parser.parse(
                req.content() != null ? req.content() : "",
                req.sourcePath(),
                req.parseMode()
        );
        return toResponse(parsed);
    }

    @PostMapping("/parse-markdown-file")
    public Map<String, Object> parseFile(@RequestBody ParseFileRequest req) {
        Path path = Path.of(req.path()).normalize().toAbsolutePath();

        // Guard 1: extension whitelist
        String name = path.getFileName().toString().toLowerCase();
        if (ALLOWED_EXTENSIONS.stream().noneMatch(name::endsWith)) {
            throw new IllegalArgumentException("Only .md / .markdown files allowed");
        }

        // Guard 2: must be docs/changes/<TICKET>/review-checklist.md
        String normalizedPath = path.toString().replace('\\', '/');
        if (!normalizedPath.contains("/changes/") || !normalizedPath.endsWith("/review-checklist.md")) {
            throw new IllegalArgumentException(
                "Only <root>/changes/<TICKET>/review-checklist.md can be parsed"
            );
        }

        // Guard 3: prevent path traversal
        Path cwd = Path.of("").toAbsolutePath();
        if (!path.startsWith(cwd)) {
            throw new IllegalArgumentException("Path must be inside backend working directory");
        }

        try {
            String content = Files.readString(path);
            ParsedArtifact parsed = parser.parse(content, path.toString(), req.parseMode());
            Map<String, Object> body = toResponse(parsed);
            return Map.of("filePath", path.toString(), "result", body);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Cannot read file: " + e.getMessage());
        }
    }

    private Map<String, Object> toResponse(ParsedArtifact parsed) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sourcePath", parsed.sourcePath());
        response.put("ticketId", parsed.ticketId());
        response.put("parseMode", parsed.parseMode());
        response.put("parseStatus", parsed.parseStatus());
        response.put("artifactStatus", parsed.artifactStatus());
        response.put("artifactExists", parsed.artifactExists());
        response.put("frontMatter", parsed.frontMatter());
        response.put("headerMetadata", parsed.headerMetadata());
        response.put("sections", parsed.sections());
        response.put("tables", parsed.tables());
        response.put("acceptanceCriteria", parsed.acceptanceCriteria());
        response.put("placeholders", parsed.placeholders());
        response.put("warnings", parsed.warnings());
        response.put("errors", parsed.errors());
        response.put("requiredFieldsMissing", parsed.requiredFieldsMissing());
        response.put("normalizedContent", parsed.normalizedContent());
        response.put("contentHashSha256", parsed.contentHash());
        response.put("parserVersion", parsed.parserVersion());
        response.put("parsedSummary", parsed.parsedSummary());
        return response;
    }

    public record ParseRequest(@NotBlank String content, String sourcePath, String parseMode) {}

    public record ParseFileRequest(@NotBlank String path, String parseMode) {}
}