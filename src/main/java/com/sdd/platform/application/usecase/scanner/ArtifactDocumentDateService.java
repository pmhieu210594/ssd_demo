package com.sdd.platform.application.usecase.scanner;

import com.sdd.platform.application.port.out.integration.ArtifactScannerSourcePort;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactSnapshot;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PHASE-DWELL-TIME: extracts the self-declared {@code create_date}/{@code update_date}
 * header values from each artifact .md file, for storage in
 * {@code tbl_fact_artifact_document_date}. Read-only, additive; does not
 * touch the 4 specialized parsers or {@code updateSnapshotParsedSummary}.
 */
@Service
public class ArtifactDocumentDateService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactDocumentDateService.class);

    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    private static final DateTimeFormatter DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ArtifactScannerSourcePort source;
    private final MarkdownParserCore markdownParserCore = new MarkdownParserCore();

    public ArtifactDocumentDateService(ArtifactScannerSourcePort source) {
        this.source = source;
    }

    public record DocumentDate(UUID artifactSnapshotId, OffsetDateTime createAt, OffsetDateTime updateAt) {
    }

    /**
     * Reads back the blob of each existing file snapshot for the ticket and
     * parses its header {@code create_date}/{@code update_date}. Files with no
     * tree entry (not present at this revision) are skipped.
     */
    public List<DocumentDate> extract(String repoFullName,
            String ticketId,
            Map<String, ArtifactSnapshot> snapshotsByFileName,
            Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree,
            String ticketDirPrefix) {
        List<DocumentDate> results = new ArrayList<>();
        for (Map.Entry<String, ArtifactSnapshot> entry : snapshotsByFileName.entrySet()) {
            String fileName = entry.getKey();
            ArtifactSnapshot snapshot = entry.getValue();
            if (snapshot == null || !snapshot.existsFlag()) {
                continue;
            }
            String sourcePath = ticketDirPrefix + fileName;
            ArtifactScannerSourcePort.GitHubTreeEntry treeEntry = tree.get(sourcePath);
            if (treeEntry == null) {
                continue;
            }
            try {
                byte[] blob = source.readBlob(repoFullName, treeEntry.sha());
                String content = new String(blob, StandardCharsets.UTF_8);
                MarkdownParserCore.MarkdownDocument document = markdownParserCore.parse(content, sourcePath);
                Map<String, String> headerMetadata = document.headerMetadata();
                OffsetDateTime createAt = parseHeaderDate(headerMetadata.get("create_date"));
                OffsetDateTime updateAt = parseHeaderDate(headerMetadata.get("update_date"));
                if (createAt == null && headerMetadata.get("create_date") != null) {
                    log.warn(
                            "ArtifactDocumentDateService: failed to parse create_date/update_date header, ticketId={}, sourcePath={}, missingField={}",
                            ticketId, sourcePath, "create_date");
                }
                if (updateAt == null && headerMetadata.get("update_date") != null) {
                    log.warn(
                            "ArtifactDocumentDateService: failed to parse create_date/update_date header, ticketId={}, sourcePath={}, missingField={}",
                            ticketId, sourcePath, "update_date");
                }
                results.add(new DocumentDate(snapshot.artifactSnapshotId(), createAt, updateAt));
            } catch (RuntimeException ex) {
                log.warn(
                        "ArtifactDocumentDateService: failed to parse create_date/update_date header, ticketId={}, sourcePath={}, missingField={}",
                        ticketId, sourcePath, "content");
            }
        }
        return results;
    }

    private OffsetDateTime parseHeaderDate(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String value = rawValue.trim();
        for (DateTimeFormatter formatter : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(value, formatter).atOffset(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        try {
            return LocalDate.parse(value, DATE_ONLY_FORMAT).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
