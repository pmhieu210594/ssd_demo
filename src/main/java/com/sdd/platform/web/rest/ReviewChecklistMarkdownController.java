package com.sdd.platform.web.rest;

import com.sdd.platform.domain.service.markdown.reviewchecklist.ReviewChecklistMarkdownParser;
import com.sdd.platform.domain.service.markdown.reviewchecklist.ReviewChecklistMarkdownParser.ParsedArtifact;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/markdown/review-checklist")
public class ReviewChecklistMarkdownController {

    private final ReviewChecklistMarkdownParser parser;

    public ReviewChecklistMarkdownController(ReviewChecklistMarkdownParser parser) {
        this.parser = parser;
    }

    @PostMapping("/parse")
    public ResponseEntity<ParsedArtifact> parse(
            @Valid @RequestBody ParseRequest request) {

        Map<String, Object> metadata =
                request.getMetadata() != null ? request.getMetadata() : Collections.emptyMap();

        ParsedArtifact result = parser.parse(
                request.getContent(),
                request.getSourcePath(),
                metadata
        );

        return ResponseEntity.ok(result);
    }

    // ===== REQUEST DTO =====
    public static class ParseRequest {

        private String content;

        private String sourcePath;

        private Map<String, Object> metadata;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getSourcePath() {
            return sourcePath;
        }

        public void setSourcePath(String sourcePath) {
            this.sourcePath = sourcePath;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }
}