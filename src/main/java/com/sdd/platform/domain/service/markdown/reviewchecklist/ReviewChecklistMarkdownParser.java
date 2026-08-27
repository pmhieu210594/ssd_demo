package com.sdd.platform.domain.service.markdown.reviewchecklist;

import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownDocument;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReviewChecklistMarkdownParser {

    private final MarkdownParserCore core = new MarkdownParserCore();

    // ===== REGEX =====
    private static final Pattern CHECKLIST_PATTERN =
            Pattern.compile("^\\s*[-*]\\s+((\\[[ xX]\\])\\s+)?");

    private static final Pattern TICKET_PATTERN =
            Pattern.compile("(TICKET[-_ ]?\\d+)", Pattern.CASE_INSENSITIVE);

    // ===== KEYWORDS =====
    private static final List<String> SECURITY = List.of("security", "auth", "secret");
    private static final List<String> TEST = List.of("test", "qa");
    private static final List<String> PERFORMANCE = List.of("performance", "latency");

    public ParsedArtifact parse(String content,
                                String sourcePath,
                                Map<String, Object> metadata) {

        ParsedArtifact result = new ParsedArtifact();

        // ADD: init metadata
        result.setArtifactType("review_checklist");
        result.setWarnings(new ArrayList<>());
        result.setErrors(new ArrayList<>());
        result.setSourcePath(sourcePath);

        // 1. validate
        if (content == null || content.trim().isEmpty()) {
            result.getErrors().add("Empty content");
            result.setParseStatus(ParserResultStatus.FAIL);
            return result;
        }

        // ADD: preprocess remove code block
        content = removeCodeBlocks(content);

        // 2. parse via core
        MarkdownDocument doc = core.parse(content, sourcePath);

        // 3. checklist count
        int checklistCount = countChecklist(doc);
        result.setChecklistItemCount(checklistCount);

        if (checklistCount == 0) {
            result.getWarnings().add("No checklist items found");
        }

        // 4. ticket id
        String ticketId = extractTicketId(doc, metadata);
        result.setTicketId(ticketId);

        if (ticketId == null || ticketId.isEmpty()) {
            result.getWarnings().add("Missing ticket_id");
        }

        // 5. perspective
        detectPerspectives(doc, result);

        if (result.getPerspectiveCount() == 0) {
            result.getWarnings().add("No perspective detected");
        }

        // ADD: source hash
        result.setSourceHash(hashContent(content));

        // 6. status (MODIFIED)
        result.setParseStatus(evaluateStatus(result));

        return result;
    }

    // ===== CHECKLIST =====
    private int countChecklist(MarkdownDocument doc) {
        int count = 0;

        String[] lines = doc.normalizedContent().split("\\R");

        for (String line : lines) {
            if (CHECKLIST_PATTERN.matcher(line).find()) {
                count++;
            }
        }

        return count;
    }

    // ===== TICKET =====
    private String extractTicketId(MarkdownDocument doc, Map<String, Object> metadata) {

        // 1. metadata param
        if (metadata != null && metadata.containsKey("ticket_id")) {
            Object val = metadata.get("ticket_id");
            if (val != null) return val.toString();
        }

        // 2. front matter
        if (doc.frontMatter().containsKey("ticket_id")) {
            return doc.frontMatter().get("ticket_id");
        }

        // 3. header metadata
        if (doc.headerMetadata().containsKey("ticket_id")) {
            return doc.headerMetadata().get("ticket_id");
        }

        // 4. fallback scan normalized content
        Matcher m = TICKET_PATTERN.matcher(doc.normalizedContent());
        if (m.find()) return m.group(1);

        return null;
    }

    // ===== PERSPECTIVE =====

    private void detectPerspectives(MarkdownDocument doc, ParsedArtifact result) {

        String text = doc.normalizedContent().toLowerCase();

        result.setHasSecurityPerspective(containsAny(text, SECURITY));
        result.setHasTestPerspective(containsAny(text, TEST));
        result.setHasPerformancePerspective(containsAny(text, PERFORMANCE));

        int count = 0;
        if (result.isHasSecurityPerspective()) count++;
        if (result.isHasTestPerspective()) count++;
        if (result.isHasPerformancePerspective()) count++;

        result.setPerspectiveCount(count);
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String k : keywords) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    // ===== VALIDATION =====
    // MODIFIED: dùng warnings/errors thay vì logic cũ
    private ParserResultStatus evaluateStatus(ParsedArtifact result) {

        if (!result.getErrors().isEmpty()) {
            return ParserResultStatus.FAIL;
        }

        if (!result.getWarnings().isEmpty()) {
            return ParserResultStatus.WARNING;
        }

        return ParserResultStatus.SUCCESS;
    }

    // ===== HELPERS =====
    private String removeCodeBlocks(String content) {
        return content.replaceAll("(?s)```.*?```", "");
    }

    private String hashContent(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ===== DTO =====
    public static class ParsedArtifact {
        private String ticketId;
        private int checklistItemCount;

        private boolean hasSecurityPerspective;
        private boolean hasTestPerspective;
        private boolean hasPerformancePerspective;
        private int perspectiveCount;

        private ParserResultStatus parseStatus;

        // ADD
        private List<String> warnings;
        private List<String> errors;
        private String artifactType;
        private String sourceHash;
        private String sourcePath;

        public String getTicketId() { return ticketId; }
        public void setTicketId(String ticketId) { this.ticketId = ticketId; }

        public int getChecklistItemCount() { return checklistItemCount; }
        public void setChecklistItemCount(int checklistItemCount) {
            this.checklistItemCount = checklistItemCount;
        }

        public boolean isHasSecurityPerspective() { return hasSecurityPerspective; }
        public void setHasSecurityPerspective(boolean val) { this.hasSecurityPerspective = val; }

        public boolean isHasTestPerspective() { return hasTestPerspective; }
        public void setHasTestPerspective(boolean val) { this.hasTestPerspective = val; }

        public boolean isHasPerformancePerspective() { return hasPerformancePerspective; }
        public void setHasPerformancePerspective(boolean val) { this.hasPerformancePerspective = val; }

        public int getPerspectiveCount() { return perspectiveCount; }
        public void setPerspectiveCount(int val) { this.perspectiveCount = val; }

        public ParserResultStatus getParseStatus() { return parseStatus; }
        public void setParseStatus(ParserResultStatus parseStatus) {
            this.parseStatus = parseStatus;
        }

        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }

        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }

        public String getArtifactType() { return artifactType; }
        public void setArtifactType(String artifactType) { this.artifactType = artifactType; }

        public String getSourceHash() { return sourceHash; }
        public void setSourceHash(String sourceHash) { this.sourceHash = sourceHash; }

        public String getSourcePath() { return sourcePath; }

        public void setSourcePath(String sourcePath) {
            this.sourcePath = sourcePath;
        }
    }

    public enum ParserResultStatus {
        SUCCESS,
        WARNING,
        FAIL
    }
}