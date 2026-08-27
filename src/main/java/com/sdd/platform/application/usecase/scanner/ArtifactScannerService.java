package com.sdd.platform.application.usecase.scanner;

import com.sdd.platform.application.port.out.integration.ArtifactScannerSourcePort;
import com.sdd.platform.application.port.out.persistence.ArtifactScannerPersistencePort;
import com.sdd.platform.application.usecase.docparse.DocParseModels;
import com.sdd.platform.application.usecase.docparse.ImplPlanParseService;
import com.sdd.platform.application.usecase.docparse.TestPlanParseService;
import com.sdd.platform.application.usecase.docparse.TestResultsParseService;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreService;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactScanMode;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactScanRequest;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactScanStatus;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactSnapshot;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactTypeScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ConnectorScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.RepositoryScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ScanRun;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.TicketScope;
import com.sdd.platform.domain.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import com.sdd.platform.domain.service.markdown.reviewchecklist.ReviewChecklistMarkdownParser;
import com.sdd.platform.domain.service.markdown.selfreview.SelfReviewMarkdownParser;
import com.sdd.platform.domain.service.markdown.specpack.SpecPackMarkdownParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ArtifactScannerService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactScannerService.class);
    private static final String CONNECTOR_TYPE = "ARTIFACT_SCANNER";

    private static final List<String> CHANGE_TARGET_FILES = List.of(
            "spec-pack.md",
            "impl-plan.md",
            "review-checklist.md",
            "self-review.md",
            "test-plan.md",
            "test-results.md",
            "report.md",
            "blackbox-testcases.md"
    );

    private static final List<String> PHASE0_TARGET_FILES = List.of(
            "README.md",
            "phase0-plan.md",
            "phase0-execution-log.md",
            "phase0-decisions.md",
            "phase0-risk-register.md",
            "phase0-review.md",
            "source-availability.md"
    );

    private final ArtifactScannerSourcePort source;
    private final ArtifactScannerPersistencePort persistence;
    private final SpecPackMarkdownParser specPackParser;
    private final SelfReviewMarkdownParser selfReviewParser;
    private final ImplPlanParseService implPlanParser;
    private final TestPlanParseService testPlanParser;
    private final TestResultsParseService testResultsParser;
    private final EvidenceQualityScoreService evidenceQualityScoreService;
    private final ReviewChecklistMarkdownParser reviewChecklistParser;

    @Autowired
    public ArtifactScannerService(ArtifactScannerSourcePort source,
                                  ArtifactScannerPersistencePort persistence,
                                  SpecPackMarkdownParser specPackParser,
                                  SelfReviewMarkdownParser selfReviewParser,
                                  ImplPlanParseService implPlanParser,
                                  TestPlanParseService testPlanParser,
                                  TestResultsParseService testResultsParser,
                                  EvidenceQualityScoreService evidenceQualityScoreService,
                                  ReviewChecklistMarkdownParser reviewChecklistParser) {
        this.source = source;
        this.persistence = persistence;
        this.specPackParser = specPackParser;
        this.selfReviewParser = selfReviewParser;
        this.implPlanParser = implPlanParser;
        this.testPlanParser = testPlanParser;
        this.testResultsParser = testResultsParser;
        this.evidenceQualityScoreService = evidenceQualityScoreService;
        this.reviewChecklistParser = reviewChecklistParser;
    }

    /**
     * Backward-compatible constructor used by unit tests and callers
     * that don't provide parser beans. It delegates to the main
     * constructor with default parser instances.
     */
    public ArtifactScannerService(ArtifactScannerSourcePort source,
                                  ArtifactScannerPersistencePort persistence) {
        this(source, persistence, new SpecPackMarkdownParser(), new SelfReviewMarkdownParser(), null, null, null, null, new ReviewChecklistMarkdownParser());
    }

    public ScanRun scan(ArtifactScanRequest request) {
        validateRequest(request);

        RepositoryScope repository = persistence.findRepository(request.repositoryId())
                .orElseThrow(() -> new NotFoundException("REPOSITORY_NOT_FOUND"));
        ConnectorScope connector = persistence.findConnectorByType(CONNECTOR_TYPE)
                .orElseThrow(() -> new IllegalStateException("CONNECTOR_NOT_CONFIGURED"));

        ScanRun run = persistence.insertRun(new ScanRun(
                null,
                connector.connectorId(),
                OffsetDateTime.now(ZoneOffset.UTC),
                null,
                "RUNNING",
                0,
                0,
                null,
                request.traceId()
        ));

        int recordsRead = 0;
        int recordsWritten = 0;
        String fatalError = null;

        try {
            ArtifactScannerSourcePort.ResolvedRevision revision =
                    source.resolveRevision(repository.repoNameMasked(), request.branchOrRef());
            Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree =
                    source.listTree(repository.repoNameMasked(), revision.revisionSha());

            List<ArtifactTypeScope> allArtifactTypes = persistence.findArtifactTypes();
            Map<String, ArtifactTypeScope> changeTypes = artifactTypesByFileName(
                    allArtifactTypes.stream()
                            .filter(type -> CHANGE_TARGET_FILES.contains(type.defaultFileName()))
                            .toList());
            Map<String, ArtifactTypeScope> phase0Types = artifactTypesByFileName(
                    allArtifactTypes.stream()
                            .filter(type -> PHASE0_TARGET_FILES.contains(type.defaultFileName()))
                            .toList());

            List<String> ticketKeys = request.scanMode() == ArtifactScanMode.FULL
                    ? listTicketDirectories(tree)
                    : request.ticketIds().stream()
                            .map(value -> value == null ? "" : value.trim())
                            .filter(value -> !value.isBlank())
                            .toList();
            if (request.scanMode() == ArtifactScanMode.TICKET_SCOPED && ticketKeys.isEmpty()) {
                log.warn("Artifact scanner skipped ticket-scoped scan for repository {} because no valid ticket ids were provided", request.repositoryId());
            }

            for (String ticketKey : ticketKeys) {
                ScanCounts counts = scanTicketDirectory(
                        run.connectorRunId(),
                        repository.repositoryId(),
                        repository.projectId(),
                        request.traceId(),
                        ticketKey,
                        changeTypes,
                        tree,
                        revision,
                        repository.repoNameMasked());
                recordsRead += counts.read();
                recordsWritten += counts.written();
            }

            if (request.scanMode() == ArtifactScanMode.FULL || request.scanMode() == ArtifactScanMode.TICKET_SCOPED) {
                ArtifactScannerSourcePort.ResolvedRevision phase0Revision = revision;
                Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> phase0Tree = tree;
                if (request.scanMode() == ArtifactScanMode.TICKET_SCOPED && !hasPhase0Path(tree)) {
                    phase0Revision = source.resolveRevision(repository.repoNameMasked(), repository.defaultBranch());
                    phase0Tree = source.listTree(repository.repoNameMasked(), phase0Revision.revisionSha());
                    log.debug("Artifact scanner fallback to default branch {} for phase0 files in repository {}",
                            repository.defaultBranch(), repository.repoNameMasked());
                }

                ScanCounts phase0Counts = scanPhase0(
                        run.connectorRunId(),
                        repository.repositoryId(),
                        phase0Types,
                        phase0Tree,
                        phase0Revision,
                        repository.repoNameMasked());
                recordsRead += phase0Counts.read();
                recordsWritten += phase0Counts.written();
            }

            recalculateScoresAfterScan(repository.projectId(), ticketKeys, request.traceId());
        } catch (RuntimeException ex) {
            fatalError = ex.getMessage();
            log.error("Artifact scanner failed for repository {}", request.repositoryId(), ex);
        } finally {
            ScanRun finished = new ScanRun(
                    run.connectorRunId(),
                    run.connectorId(),
                    run.startedAt(),
                    OffsetDateTime.now(ZoneOffset.UTC),
                    fatalError == null ? "SUCCESS" : "FAILED",
                    recordsRead,
                    recordsWritten,
                    fatalError,
                    request.traceId()
            );
            persistence.updateRun(finished);
            run = finished;
        }

        if (fatalError != null) {
            throw new IllegalStateException(fatalError);
        }

        return run;
    }

    public ScanRun getRun(UUID runId) {
        return persistence.findRun(runId).orElseThrow(() -> new NotFoundException("SCAN_RUN_NOT_FOUND"));
    }

    public List<ArtifactSnapshot> getRunArtifacts(UUID runId) {
        return persistence.findRunArtifacts(runId);
    }

    public List<ArtifactSnapshot> getCurrentInventory(UUID repositoryId) {
        return persistence.findCurrentInventory(repositoryId);
    }

    private ScanCounts scanTicketDirectory(UUID runId,
                                           UUID repositoryId,
                                           UUID projectId,
                                           String traceId,
                                           String ticketKey,
                                           Map<String, ArtifactTypeScope> typesByFileName,
                                           Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree,
                                           ArtifactScannerSourcePort.ResolvedRevision revision,
                                           String repoFullName) {
        String ticketDirPrefix = resolveTicketDirPrefix(tree, ticketKey);

        TicketScope ticket = persistence.findTicketByProjectIdAndExternalKey(projectId, ticketKey)
                .orElseGet(() -> {
                    log.info("Artifact scanner auto-creating minimal ticket {} for repository {}", ticketKey, repositoryId);
                    return persistence.upsertMinimalTicket(projectId, ticketKey, ticketKey);
                });

        if (!hasPathPrefix(tree, ticketDirPrefix)) {
            log.warn("Artifact scanner missing ticket directory {} in {}", ticketDirPrefix, repoFullName);
        }

        int read = 0;
        int written = 0;
        for (String fileName : CHANGE_TARGET_FILES) {
            ArtifactTypeScope artifactType = typesByFileName.get(fileName);
            if (artifactType == null) {
                continue;
            }
            String sourcePath = ticketDirPrefix + fileName;
            ArtifactScannerSourcePort.GitHubTreeEntry entry = tree.get(sourcePath);
            ArtifactSnapshot snapshot = buildSnapshot(
                    runId,
                    repositoryId,
                    ticket.ticketId(),
                    ticket.externalTicketKey(),
                    null,
                    null,
                    sourcePath,
                    entry,
                    revision,
                    artifactType,
                    repoFullName);
            snapshot = persistence.insertSnapshot(snapshot);

            if ("spec-pack.md".equalsIgnoreCase(artifactType.defaultFileName()) && !snapshot.existsFlag()) {
                persistence.deactivateAcceptanceCriteriaByTicketId(ticket.ticketId());
                persistence.deleteParsedSectionsByTicketIdAndSectionType(ticket.ticketId(), "spec-pack");
            }
            
            // If this is a parser-backed markdown artifact and it needs parsing,
            // run the dedicated parser now so the scanner pipeline performs parsing
            // immediately after PRs.
            if (snapshot.needParse() && snapshot.existsFlag()) {
                String artifactFileName = artifactType.defaultFileName();
                if ("spec-pack.md".equalsIgnoreCase(artifactFileName)) {
                    try {
                        byte[] blob = source.readBlob(repoFullName, entry.sha());
                        String content = new String(blob, StandardCharsets.UTF_8);
                        var parsed = specPackParser.parse(content, sourcePath, "draft");
                        persistSpecPackParse(snapshot, ticket.ticketId(), repositoryId, runId, sourcePath, parsed);
                    } catch (RuntimeException ex) {
                        log.warn("Spec-pack parser failed for {}: {}", sourcePath, ex.getMessage());
                        persistParseFailure(snapshot, ticket.ticketId(), repositoryId, runId, "SPEC_PACK_PARSE", ex.getMessage());
                    }
                } else if ("self-review.md".equalsIgnoreCase(artifactFileName)) {
                    try {
                        byte[] blob = source.readBlob(repoFullName, entry.sha());
                        String content = new String(blob, StandardCharsets.UTF_8);
                        var parsed = selfReviewParser.parse(content, sourcePath, "draft");
                        persistSelfReviewParse(snapshot, ticket.ticketId(), repositoryId, runId, sourcePath, parsed);
                    } catch (RuntimeException ex) {
                        log.warn("Self-review parser failed for {}: {}", sourcePath, ex.getMessage());
                        persistParseFailure(snapshot, ticket.ticketId(), repositoryId, runId, "SELF_REVIEW_PARSE", ex.getMessage());
                    }
                } else if ("impl-plan.md".equalsIgnoreCase(artifactFileName)) {
                    parseDocArtifact(snapshot, sourcePath, repoFullName, entry, ticket.ticketId(), projectId, repositoryId, runId, traceId, implPlanParser, "impl-plan-parser");
                } else if ("test-plan.md".equalsIgnoreCase(artifactFileName)) {
                    parseDocArtifact(snapshot, sourcePath, repoFullName, entry, ticket.ticketId(), projectId, repositoryId, runId, traceId, testPlanParser, "test-plan-parser");
                } else if ("test-results.md".equalsIgnoreCase(artifactFileName)) {
                    parseDocArtifact(snapshot, sourcePath, repoFullName, entry, ticket.ticketId(), projectId, repositoryId, runId, traceId, testResultsParser, "test-results-parser");
                } else if ("review-checklist.md".equalsIgnoreCase(artifactFileName)) {
                     try {
                        byte[] blob = source.readBlob(repoFullName, entry.sha());
                        String content = new String(blob, StandardCharsets.UTF_8);
                        var parsed = reviewChecklistParser.parse(content, sourcePath, Map.of());
                        persistReviewChecklistParse(snapshot,ticket.ticketId(),repositoryId,runId,sourcePath,parsed);
                    } catch (RuntimeException ex) {
                        log.warn("Review-checklist parser failed for {}: {}", sourcePath, ex.getMessage());
                        persistParseFailure(snapshot,ticket.ticketId(),repositoryId,runId,"REVIEW_CHECKLIST_PARSE",ex.getMessage());
                    }
                }
            }
            read++;
            written++;
        }
        return new ScanCounts(read, written);
    }

    private void recalculateScoresAfterScan(UUID projectId, List<String> ticketKeys, String requestedBy) {
        if (evidenceQualityScoreService == null || ticketKeys == null || ticketKeys.isEmpty()) {
            return;
        }
        for (String ticketKey : ticketKeys.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList()) {
            persistence.findTicketByProjectIdAndExternalKey(projectId, ticketKey)
                    .ifPresent(ticket -> evidenceQualityScoreService.recalculateFromSourceChange(
                            ticket.ticketId(),
                            null,
                            requestedBy));
        }
    }

    private void parseDocArtifact(ArtifactSnapshot snapshot,
                                  String sourcePath,
                                  String repoFullName,
                                  ArtifactScannerSourcePort.GitHubTreeEntry entry,
                                  UUID ticketId,
                                  UUID projectId,
                                  UUID repositoryId,
                                  UUID runId,
                                  String traceId,
                                  Object parser,
                                  String parserName) {
        if (parser == null) {
            return;
        }
        try {
            byte[] blob = source.readBlob(repoFullName, entry.sha());
            String content = new String(blob, StandardCharsets.UTF_8);
            DocParseModels.ParseRequest request = new DocParseModels.ParseRequest(
                    projectId,
                    repositoryId,
                    ticketId,
                    DocParseModels.ParseMode.DRAFT,
                    sourcePath,
                    content,
                    parserName,
                    "v1",
                    traceId == null || traceId.isBlank() ? runId.toString() : traceId,
                    "CI_PENDING"
            );
            if (parser instanceof ImplPlanParseService implPlanParseService) {
                implPlanParseService.parseAndStore(request);
            } else if (parser instanceof TestPlanParseService testPlanParseService) {
                testPlanParseService.parseAndStore(request);
            } else if (parser instanceof TestResultsParseService testResultsParseService) {
                testResultsParseService.parseAndStore(request);
            }
        } catch (RuntimeException ex) {
            log.warn("{} parser failed for {}: {}", parserName, sourcePath, ex.getMessage());
            persistParseFailure(snapshot, ticketId, repositoryId, runId, parserName.toUpperCase(Locale.ROOT).replace("-", "_"), ex.getMessage());
        }
    }

    private void persistSpecPackParse(ArtifactSnapshot snapshot,
                                     UUID ticketId,
                                     UUID repositoryId,
                                     UUID runId,
                                     String sourcePath,
                                     SpecPackMarkdownParser.ParsedArtifact parsed) {
        log.info("Spec-pack parsed for {}: parseStatus={}, warnings={}, errors={}", sourcePath, parsed.parseStatus(), parsed.warnings().size(), parsed.errors().size());
        log.debug("Spec-pack parse details for {}: required_fields_missing={}, ac_count={}, section_count={}",
                sourcePath, parsed.requiredFieldsMissing().size(), parsed.acceptanceCriteria().size(), parsed.sections().size());

        persistence.updateSnapshotParsedSummary(new ArtifactScannerModels.ParsedSummaryPatch(
                snapshot.artifactSnapshotId(),
                parsed.parsedSummary(),
                parsed.requiredFieldsMissing(),
                parsed.parserVersion(),
                parsed.errors().isEmpty()
        ));

        persistence.deactivateAcceptanceCriteriaByTicketId(ticketId);
        persistence.deleteParsedSectionsByTicketIdAndSectionType(ticketId, "spec-pack");
        for (var ac : parsed.acceptanceCriteria()) {
            persistence.insertParsedAcceptanceCriteria(new ArtifactScannerModels.ParsedAcceptanceCriteria(
                    snapshot.artifactSnapshotId(),
                    ticketId,
                    ac.id(),
                    ac.description(),
                    computeSha256(ac.description()),
                    !ac.validFormat()
            ));
        }

        for (var entrySection : parsed.sections().entrySet()) {
            String sectionKey = entrySection.getKey();
            String sectionText = entrySection.getValue();
            if (sectionText == null || sectionText.trim().isEmpty()) {
                continue;
            }
            persistence.insertParsedSection(new ArtifactScannerModels.ParsedSection(
                    snapshot.artifactSnapshotId(),
                    ticketId,
                    "spec-pack",
                    sectionKey,
                    sectionText != null && sectionText.length() > 500 ? sectionText.substring(0, 500) : sectionText,
                    computeSha256(sectionText != null ? sectionText : ""),
                    isRequiredSection(sectionKey),
                    !parsed.requiredFieldsMissing().contains("section:" + sectionKey),
                    parsed.errors().isEmpty(),
                    null
            ));
        }

        persistence.insertEvidenceEvent(new ArtifactScannerModels.EvidenceEvent(
                snapshot.artifactSnapshotId(),
                ticketId,
                repositoryId,
                runId,
                "SPEC_PACK_PARSE",
                "PARSE_COMPLETED",
                parsed.errors().isEmpty() ? "SUCCESS" : "PARTIAL",
                "Spec-pack parsed: " + parsed.parseStatus(),
                String.format(Locale.ROOT, "{\"parseStatus\":\"%s\",\"warnings\":%d,\"errors\":%d}", parsed.parseStatus(), parsed.warnings().size(), parsed.errors().size())
        ));

        if (!parsed.requiredFieldsMissing().isEmpty() || !parsed.errors().isEmpty()) {
            persistence.insertDataQualityRecord(new ArtifactScannerModels.DataQualityRecord(
                    runId,
                    repositoryId,
                    "SPEC_PACK_PARSE",
                    sourcePath,
                    parsed.requiredFieldsMissing().size(),
                    parsed.errors().size(),
                    0,
                    String.join("; ", parsed.errors().stream().map(e -> e.message()).toList())
            ));
        }
        if (evidenceQualityScoreService != null && ticketId != null) {
            evidenceQualityScoreService.recalculateFromParser(ticketId, null, runId.toString());
        }
    }

    private void persistSelfReviewParse(ArtifactSnapshot snapshot,
                                        UUID ticketId,
                                        UUID repositoryId,
                                        UUID runId,
                                        String sourcePath,
                                        SelfReviewMarkdownParser.ParsedArtifact parsed) {
        log.info("Self-review parsed for {}: parseStatus={}, warnings={}, errors={}", sourcePath, parsed.parseStatus(), parsed.warnings().size(), parsed.errors().size());
        log.debug("Self-review parse details for {}: required_sections_missing={}, table_count={}, section_count={}, final_verdict={}",
                sourcePath, parsed.requiredSectionsMissing().size(), parsed.tables().size(), parsed.sections().size(), parsed.finalVerdict());

        persistence.updateSnapshotParsedSummary(new ArtifactScannerModels.ParsedSummaryPatch(
                snapshot.artifactSnapshotId(),
                parsed.parsedSummary(),
                parsed.requiredSectionsMissing(),
                parsed.parserVersion(),
                parsed.errors().isEmpty()
        ));

        for (var section : parsed.sections()) {
            String sectionKey = section.canonicalKey();
            String sectionText = section.body();
            boolean present = isPresentSelfReviewSection(sectionKey, sectionText, parsed);
            persistence.insertParsedSection(new ArtifactScannerModels.ParsedSection(
                    snapshot.artifactSnapshotId(),
                    ticketId,
                    "self-review",
                    sectionKey,
                    sectionText != null && sectionText.length() > 500 ? sectionText.substring(0, 500) : sectionText,
                    computeSha256(sectionText != null ? sectionText : ""),
                    isRequiredSelfReviewSection(sectionKey),
                    present,
                    parsed.errors().isEmpty() && present,
                    null
            ));
        }

        persistence.insertEvidenceEvent(new ArtifactScannerModels.EvidenceEvent(
                snapshot.artifactSnapshotId(),
                ticketId,
                repositoryId,
                runId,
                "SELF_REVIEW_PARSE",
                "PARSE_COMPLETED",
                parsed.errors().isEmpty() ? "SUCCESS" : "PARTIAL",
                "Self-review parsed: " + parsed.parseStatus(),
                String.format(Locale.ROOT, "{\"parseStatus\":\"%s\",\"warnings\":%d,\"errors\":%d,\"finalVerdict\":\"%s\"}",
                        parsed.parseStatus(), parsed.warnings().size(), parsed.errors().size(), String.valueOf(parsed.finalVerdict()))
        ));

        if (!parsed.requiredSectionsMissing().isEmpty() || !parsed.errors().isEmpty()) {
            persistence.insertDataQualityRecord(new ArtifactScannerModels.DataQualityRecord(
                    runId,
                    repositoryId,
                    "SELF_REVIEW_PARSE",
                    sourcePath,
                    parsed.requiredSectionsMissing().size(),
                    parsed.errors().size(),
                    0,
                    String.join("; ", parsed.errors().stream().map(e -> e.message()).toList())
            ));
        }
        if (evidenceQualityScoreService != null && ticketId != null) {
            evidenceQualityScoreService.recalculateFromParser(ticketId, null, runId.toString());
        }
    }

    private void persistParseFailure(ArtifactSnapshot snapshot,
                                     UUID ticketId,
                                     UUID repositoryId,
                                     UUID runId,
                                     String sourceType,
                                     String message) {
        try {
            persistence.insertEvidenceEvent(new ArtifactScannerModels.EvidenceEvent(
                    snapshot.artifactSnapshotId(),
                    ticketId,
                    repositoryId,
                    runId,
                    sourceType,
                    "PARSE_FAILED",
                    "FAILED",
                    message,
                    "{}"
            ));
        } catch (Exception eventEx) {
            log.debug("Failed to log parse failure event", eventEx);
        }
    }

    private void persistReviewChecklistParse(
        ArtifactSnapshot snapshot,
        UUID ticketId,
        UUID repositoryId,
        UUID runId,
        String sourcePath,
        ReviewChecklistMarkdownParser.ParsedArtifact parsed) {

        log.info("Review-checklist parsed for {}: parseStatus={}, warnings={}, errors={}",
                sourcePath,
                parsed.getParseStatus(),
                parsed.getWarnings().size(),
                parsed.getErrors().size());

        log.debug("Review-checklist details for {}: checklist_count={}, perspective_count={}",
                sourcePath,
                parsed.getChecklistItemCount(),
                parsed.getPerspectiveCount());

        Map<String, Object> summary = Map.of(
                "checklist_item_count", parsed.getChecklistItemCount(),
                "perspective_count", parsed.getPerspectiveCount(),
                "has_security_perspective", parsed.isHasSecurityPerspective(),
                "has_test_perspective", parsed.isHasTestPerspective(),
                "has_performance_perspective", parsed.isHasPerformancePerspective()
        );

        persistence.updateSnapshotParsedSummary(
                new ArtifactScannerModels.ParsedSummaryPatch(
                        snapshot.artifactSnapshotId(),
                        summary,
                        parsed.getWarnings(),
                        "v1",
                        parsed.getErrors().isEmpty()
                )
        );

        persistence.insertEvidenceEvent(
                new ArtifactScannerModels.EvidenceEvent(
                        snapshot.artifactSnapshotId(),
                        ticketId,
                        repositoryId,
                        runId,
                        "REVIEW_CHECKLIST_PARSE",
                        "PARSE_COMPLETED",
                        parsed.getErrors().isEmpty() ? "SUCCESS" : "PARTIAL",
                        "Review-checklist parsed: " + parsed.getParseStatus(),
                        String.format(
                                Locale.ROOT,
                                "{\"parseStatus\":\"%s\",\"warnings\":%d,\"errors\":%d}",
                                parsed.getParseStatus(),
                                parsed.getWarnings().size(),
                                parsed.getErrors().size()
                        )
                )
        );
    }

    private String computeSha256(String input) {

        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8))
                    .toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            log.warn("SHA-256 algorithm not available, using empty hash", e);
            return "";
        }
    }

    private String normalizeSectionType(String sectionKey) {
        if (sectionKey == null) return null;
        return sectionKey.toUpperCase().replace(" ", "_").replace("-", "_");
    }

    private boolean isRequiredSection(String sectionKey) {
        String normalized = normalizeSectionType(sectionKey);
        return REQUIRED_SPEC_PACK_SECTIONS.contains(normalized);
    }

    private boolean isRequiredSelfReviewSection(String sectionKey) {
        String normalized = normalizeSectionType(sectionKey);
        return REQUIRED_SELF_REVIEW_SECTIONS.contains(normalized);
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

    private boolean isPresentSelfReviewSection(String sectionKey, String sectionText, SelfReviewMarkdownParser.ParsedArtifact parsed) {
        String normalized = normalizeSectionType(sectionKey);
        if (normalized == null) {
            return false;
        }
        if (SELF_REVIEW_TABLE_SECTIONS.contains(normalized)) {
            return hasMeaningfulTableContent(parsed, normalized);
        }
        return sectionText != null && !sectionText.isBlank();
    }

    private boolean hasMeaningfulTableContent(SelfReviewMarkdownParser.ParsedArtifact parsed, String sectionKey) {
        if (parsed == null || parsed.tables() == null || sectionKey == null) {
            return false;
        }
        return parsed.tables().stream()
                .filter(table -> sectionKey.equals(normalizeSectionType(table.sectionKey())))
                .anyMatch(table -> table.rows().stream()
                        .flatMap(List::stream)
                        .anyMatch(value -> value != null && !value.isBlank() && !isPlaceholderToken(value)));
    }

    private static final List<String> REQUIRED_SPEC_PACK_SECTIONS = List.of(
            "CONTEXT_PURPOSE", "SCOPE", "TERMINOLOGY", "AS_IS", "TO_BE", "DETAILED_SPECIFICATION",
            "ACCEPTANCE_CRITERIA", "EXAMPLES", "HUMAN_DECISION_REQUIRED", "OPEN_ISSUES"
    );

    private static final List<String> REQUIRED_SELF_REVIEW_SECTIONS = List.of(
            "IMPLEMENTATION_SUMMARY",
            "SPECIFICATION_AC_MATCHING",
            "LIST_OF_CHANGED_FILES",
            "RUN_COMMAND_AND_RESULTS",
            "SELF_CHECK_USING_REVIEW_CHECKLIST",
            "TEST_PLAN_CORRESPONDING_STATUS",
            "BUGS_FOUND_AND_RESOLVED",
            "UNPROCESSED_PENDING_ACCEPTED_RISK",
            "ITEMS_REVIEWED_BY_HUMANS",
            "FINAL_SELF_VERDICT"
    );

    private static final Set<String> SELF_REVIEW_TABLE_SECTIONS = Set.of(
            "SPECIFICATION_AC_MATCHING",
            "LIST_OF_CHANGED_FILES",
            "RUN_COMMAND_AND_RESULTS",
            "SELF_CHECK_USING_REVIEW_CHECKLIST",
            "BUGS_FOUND_AND_RESOLVED",
            "UNPROCESSED_PENDING_ACCEPTED_RISK"
    );

    private ScanCounts scanPhase0(UUID runId,
                                  UUID repositoryId,
                                  Map<String, ArtifactTypeScope> typesByFileName,
                                  Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree,
                                  ArtifactScannerSourcePort.ResolvedRevision revision,
                                  String repoFullName) {
        int read = 0;
        int written = 0;
        for (String fileName : PHASE0_TARGET_FILES) {
            ArtifactTypeScope artifactType = typesByFileName.get(fileName);
            if (artifactType == null) {
                continue;
            }
            String sourcePath = resolvePhase0FilePath(tree, fileName);
            ArtifactScannerSourcePort.GitHubTreeEntry entry = tree.get(sourcePath);
            ArtifactSnapshot snapshot = buildSnapshot(
                    runId,
                    repositoryId,
                    null,
                    null,
                    null,
                    null,
                    sourcePath,
                    entry,
                    revision,
                    artifactType,
                    repoFullName);
            persistence.insertSnapshot(snapshot);
            read++;
            written++;
        }
        return new ScanCounts(read, written);
    }

    private ArtifactSnapshot buildSnapshot(UUID runId,
                                           UUID repositoryId,
                                           UUID ticketId,
                                           String ticketExternalKey,
                                           String ticketStatus,
                                           OffsetDateTime ticketLastCommitAt,
                                           String sourcePath,
                                           ArtifactScannerSourcePort.GitHubTreeEntry entry,
                                           ArtifactScannerSourcePort.ResolvedRevision revision,
                                           ArtifactTypeScope artifactType,
                                           String repoFullName) {
        boolean exists = entry != null && "blob".equalsIgnoreCase(entry.type());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String scanStatus;
        String scanMessage = null;
        String contentHash = null;
        Long sizeBytes = null;
        OffsetDateTime sourceUpdatedAt = null;
        boolean templateEmpty = false;
        boolean needParse = false;

        if (exists) {
            try {
                byte[] bytes = source.readBlob(repoFullName, entry.sha());
                sizeBytes = entry.size() != null ? entry.size() : (long) bytes.length;
                contentHash = sha256(bytes);
                sourceUpdatedAt = revision.committedAt();
                templateEmpty = bytes.length == 0 || new String(bytes, StandardCharsets.UTF_8).trim().isEmpty();
                ArtifactSnapshot previous = persistence.findLatestSnapshot(repositoryId, sourcePath, artifactType.artifactTypeId()).orElse(null);
                needParse = previous == null || !Objects.equals(previous.contentHash(), contentHash);
                scanStatus = ArtifactScanStatus.FOUND.name();
                if (templateEmpty) {
                    scanMessage = "TEMPLATE_EMPTY";
                }
            } catch (RuntimeException ex) {
                scanStatus = ArtifactScanStatus.INACCESSIBLE.name();
                scanMessage = "ARTIFACT_READ_ERROR";
                exists = false;
                sizeBytes = entry.size();
            }
        } else {
            scanStatus = artifactType.requiredFlag()
                    ? ArtifactScanStatus.MISSING.name()
                    : ArtifactScanStatus.SKIPPED.name();
            scanMessage = artifactType.requiredFlag()
                    ? "REQUIRED_ARTIFACT_MISSING"
                    : "OPTIONAL_ARTIFACT_MISSING";
        }

        return new ArtifactSnapshot(
                null,
                runId,
                repositoryId,
                ticketId,
                ticketExternalKey,
                ticketStatus,
                ticketLastCommitAt,
                artifactType.artifactTypeId(),
                artifactType.phaseId(),
                artifactType.artifactTypeCode(),
                artifactType.artifactName(),
                artifactType.defaultFileName(),
                artifactType.requiredFlag(),
                artifactType.phaseCode(),
                sourcePath,
                exists,
                contentHash,
                sizeBytes,
                sourceUpdatedAt,
                templateEmpty,
                needParse,
                scanStatus,
                scanMessage,
                now
        );
    }

    private void validateRequest(ArtifactScanRequest request) {
        if (request.repositoryId() == null) {
            throw new IllegalArgumentException("repository_id is required");
        }
        if (request.branchOrRef() == null || request.branchOrRef().isBlank()) {
            throw new IllegalArgumentException("INVALID_REF");
        }
        if (request.scanMode() == null) {
            throw new IllegalArgumentException("scan_mode is required");
        }
        if (request.triggerType() == null) {
            throw new IllegalArgumentException("trigger_type is required");
        }
        if (request.requestedBy() == null || request.requestedBy().isBlank()) {
            throw new IllegalArgumentException("requested_by is required");
        }
        if (request.scanMode() == ArtifactScanMode.TICKET_SCOPED
                && (request.ticketIds() == null || request.ticketIds().isEmpty())) {
            throw new IllegalArgumentException("ticket_ids are required for TICKET_SCOPED");
        }
    }

    private List<String> listTicketDirectories(Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree) {
        return tree.keySet().stream()
                .map(ArtifactScannerService::extractTicketKeyFromChangesPath)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static String extractTicketKeyFromChangesPath(String path) {
        int idx = path.indexOf("/changes/");
        int start;
        if (idx >= 0) {
            start = idx + "/changes/".length();
        } else if (path.startsWith("changes/")) {
            start = "changes/".length();
        } else {
            return null;
        }
        int end = path.indexOf('/', start);
        if (end <= start) return null;
        String key = path.substring(start, end).trim();
        return key.isBlank() ? null : key;
    }

    private String resolveTicketDirPrefix(Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree, String ticketKey) {
        String segment = "/changes/" + ticketKey + "/";
        String fallback = "changes/" + ticketKey + "/";
        return tree.keySet().stream()
                .filter(p -> p.contains(segment) || p.startsWith(fallback))
                .map(p -> {
                    int idx = p.indexOf(segment);
                    return idx >= 0 ? p.substring(0, idx + segment.length()) : fallback;
                })
                .findFirst()
                .orElse(fallback);
    }

    private static String resolvePhase0FilePath(Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree, String fileName) {
        String suffix = "/maintenance/phase0/" + fileName;
        String fallback = "maintenance/phase0/" + fileName;
        return tree.keySet().stream()
                .filter(p -> p.endsWith(suffix) || p.equals(fallback))
                .findFirst()
                .orElse(fallback);
    }

    private boolean hasPhase0Path(Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree) {
        return tree.keySet().stream()
                .anyMatch(p -> p.contains("/maintenance/phase0/") || p.startsWith("maintenance/phase0/"));
    }

    private boolean hasPathPrefix(Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree, String prefix) {
        return tree.keySet().stream().anyMatch(path -> path.startsWith(prefix));
    }

    private Map<String, ArtifactTypeScope> artifactTypesByFileName(List<ArtifactTypeScope> types) {
        return types.stream().collect(Collectors.toMap(
                ArtifactTypeScope::defaultFileName,
                type -> type,
                (a, b) -> a,
                LinkedHashMap::new));
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to calculate content hash", ex);
        }
    }

    private record ScanCounts(int read, int written) {}
}
