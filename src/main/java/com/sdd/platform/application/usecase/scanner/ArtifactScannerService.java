package com.sdd.platform.application.usecase.scanner;

import com.sdd.platform.application.port.out.integration.ArtifactScannerSourcePort;
import com.sdd.platform.application.port.out.persistence.ArtifactScannerPersistencePort;
import com.sdd.platform.application.port.out.persistence.TestEvidencePersistencePort;
import com.sdd.platform.application.usecase.docparse.DocParseModels;
import com.sdd.platform.application.usecase.docparse.ImplPlanParseService;
import com.sdd.platform.application.usecase.docparse.TestPlanParseService;
import com.sdd.platform.application.usecase.docparse.TestResultsParseService;
import com.sdd.platform.application.usecase.phase.TicketPhaseEvaluatorService;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreService;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactScanMode;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedException;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedIssue;
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

import com.sdd.platform.domain.service.markdown.report.ReportMarkdownParser;
import com.sdd.platform.domain.service.markdown.report.ReportMarkdownParser.AcceptedRiskRow;
import com.sdd.platform.domain.service.markdown.core.HeaderDateNormalizer;
import com.sdd.platform.domain.service.markdown.core.MarkdownParserCore.MarkdownTable;
import com.sdd.platform.domain.service.markdown.reviewchecklist.ReviewChecklistMarkdownParser;
import com.sdd.platform.domain.service.markdown.selfreview.SelfReviewMarkdownParser;
import com.sdd.platform.domain.service.markdown.specpack.SpecPackMarkdownParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
            "blackbox-testcases.md");

    private static final List<String> PHASE0_TARGET_FILES = List.of(
            "README.md",
            "phase0-plan.md",
            "phase0-execution-log.md",
            "phase0-decisions.md",
            "phase0-risk-register.md",
            "phase0-review.md",
            "source-availability.md");

    private static final Set<String> REQUIRED_REVIEW_CHECKLIST_FIELDS = Set.copyOf(
            ReviewChecklistMarkdownParser.requiredSectionKeys());

    private static final String SPEC_PACK_SOURCE_TYPE = "SPEC_PACK";
    private static final String REPORT_SOURCE_TYPE = "REPORT";
    private static final String OPEN_ISSUES_SECTION_KEY = "OPEN_ISSUES";

    private final ArtifactScannerSourcePort source;
    private final ArtifactScannerPersistencePort persistence;
    private final TestEvidencePersistencePort testEvidencePersistencePort;
    private final SpecPackMarkdownParser specPackParser;
    private final SelfReviewMarkdownParser selfReviewParser;
    private final ImplPlanParseService implPlanParser;
    private final TestPlanParseService testPlanParser;
    private final TestResultsParseService testResultsParser;
    private final EvidenceQualityScoreService evidenceQualityScoreService;
    private final ReviewChecklistMarkdownParser reviewChecklistParser;
    private final ReportMarkdownParser reportMarkdownParser;
    private final TicketPhaseEvaluatorService ticketPhaseEvaluatorService;
    private final ArtifactDocumentDateService artifactDocumentDateService;

    @Autowired
    public ArtifactScannerService(ArtifactScannerSourcePort source,
            ArtifactScannerPersistencePort persistence,
            TestEvidencePersistencePort testEvidencePersistencePort,
            SpecPackMarkdownParser specPackParser,
            SelfReviewMarkdownParser selfReviewParser,
            ImplPlanParseService implPlanParser,
            TestPlanParseService testPlanParser,
            TestResultsParseService testResultsParser,
            EvidenceQualityScoreService evidenceQualityScoreService,
            ReviewChecklistMarkdownParser reviewChecklistParser,
            ReportMarkdownParser reportMarkdownParser,
            TicketPhaseEvaluatorService ticketPhaseEvaluatorService,
            ArtifactDocumentDateService artifactDocumentDateService) {
        this.source = source;
        this.persistence = persistence;
        this.testEvidencePersistencePort = testEvidencePersistencePort;
        this.specPackParser = specPackParser;
        this.selfReviewParser = selfReviewParser;
        this.implPlanParser = implPlanParser;
        this.testPlanParser = testPlanParser;
        this.testResultsParser = testResultsParser;
        this.evidenceQualityScoreService = evidenceQualityScoreService;
        this.reviewChecklistParser = reviewChecklistParser;
        this.reportMarkdownParser = reportMarkdownParser;
        this.ticketPhaseEvaluatorService = ticketPhaseEvaluatorService;
        this.artifactDocumentDateService = artifactDocumentDateService;
    }

    public ArtifactScannerService(ArtifactScannerSourcePort source,
            ArtifactScannerPersistencePort persistence,
            SpecPackMarkdownParser specPackParser,
            SelfReviewMarkdownParser selfReviewParser,
            ImplPlanParseService implPlanParser,
            TestPlanParseService testPlanParser,
            TestResultsParseService testResultsParser,
            EvidenceQualityScoreService evidenceQualityScoreService,
            ReviewChecklistMarkdownParser reviewChecklistParser,
            ReportMarkdownParser reportMarkdownParser,
            TicketPhaseEvaluatorService ticketPhaseEvaluatorService) {
        this(source, persistence, null, specPackParser, selfReviewParser, implPlanParser,
                testPlanParser, testResultsParser, evidenceQualityScoreService, reviewChecklistParser,
                reportMarkdownParser, ticketPhaseEvaluatorService, null);
    }

    /**
     * Backward-compatible constructor used by unit tests and callers
     * that don't provide parser beans. It delegates to the main
     * constructor with default parser instances.
     */
    public ArtifactScannerService(ArtifactScannerSourcePort source,
            ArtifactScannerPersistencePort persistence) {
        this(source, persistence, null, new SpecPackMarkdownParser(), new SelfReviewMarkdownParser(), null, null, null,
                null,
                new ReviewChecklistMarkdownParser(), new ReportMarkdownParser(), null, null);
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
                request.traceId()));

        int recordsRead = 0;
        int recordsWritten = 0;
        String fatalError = null;

        try {
            ArtifactScannerSourcePort.ResolvedRevision revision = source.resolveRevision(repository.repoNameMasked(),
                    request.branchOrRef());
            Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree = source.listTree(repository.repoNameMasked(),
                    revision.revisionSha());

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
                log.warn(
                        "Artifact scanner skipped ticket-scoped scan for repository {} because no valid ticket ids were provided",
                        request.repositoryId());
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
                        repository.projectId(),
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
                    request.traceId());
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
                    log.info("Artifact scanner auto-creating minimal ticket {} for repository {}", ticketKey,
                            repositoryId);
                    return persistence.upsertMinimalTicket(projectId, ticketKey, ticketKey);
                });

        if (!hasPathPrefix(tree, ticketDirPrefix)) {
            log.warn("Artifact scanner missing ticket directory {} in {}", ticketDirPrefix, repoFullName);
        }

        int read = 0;
        int written = 0;
        Map<String, ArtifactSnapshot> snapshotsByFileName = new LinkedHashMap<>();
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

            var previousOpt = persistence.findLatestSnapshot(repositoryId, sourcePath, artifactType.artifactTypeId());
            if (previousOpt.isPresent()) {
                ArtifactSnapshot previous = previousOpt.get();
                snapshot = persistence.updateSnapshot(snapshot, previous.artifactSnapshotId());
            } else {
                snapshot = persistence.insertSnapshot(snapshot);
            }
            snapshotsByFileName.put(fileName, snapshot);

            if ("spec-pack.md".equalsIgnoreCase(artifactType.defaultFileName()) && !snapshot.existsFlag()) {
                persistence.deactivateAcceptanceCriteriaByTicketId(ticket.ticketId());
                persistence.deleteParsedSectionsByTicketIdAndSectionType(ticket.ticketId(), "spec-pack");
                persistence.deleteParsedIssuesByTicketIdAndSourceType(ticket.ticketId(), SPEC_PACK_SOURCE_TYPE);
                persistence.updateTicketStartedAt(ticket.ticketId(), null);
            }
            if ("report.md".equalsIgnoreCase(artifactType.defaultFileName()) && !snapshot.existsFlag()) {
                persistence.deleteParsedSectionsByTicketIdAndSectionType(ticket.ticketId(), "report");
                persistence.deleteParsedIssuesByTicketIdAndSourceType(ticket.ticketId(), REPORT_SOURCE_TYPE);
                persistence.deleteParsedRisksByTicketId(ticket.ticketId());
                persistence.updateTicketCompletedAt(ticket.ticketId(), null);
            }
            if ("self-review.md".equalsIgnoreCase(artifactType.defaultFileName()) && !snapshot.existsFlag()) {
                persistence.deleteParsedSectionsByTicketIdAndSectionType(ticket.ticketId(), "self-review");
                persistence.deleteParsedExceptionsByTicketId(ticket.ticketId());
            }
            if ("review-checklist.md".equalsIgnoreCase(artifactType.defaultFileName()) && !snapshot.existsFlag()) {
                persistence.deleteParsedSectionsByTicketIdAndSectionType(ticket.ticketId(), "review-checklist");
            }
            if ("impl-plan.md".equalsIgnoreCase(artifactType.defaultFileName()) && !snapshot.existsFlag()) {
                persistence.deleteParsedSectionsByTicketIdAndSectionType(ticket.ticketId(), "impl-plan");
            }
            boolean testPlanMissing = "test-plan.md".equalsIgnoreCase(artifactType.defaultFileName()) && !snapshot.existsFlag();
            boolean testResultsMissing = "test-results.md".equalsIgnoreCase(artifactType.defaultFileName()) && !snapshot.existsFlag();
            if ((testPlanMissing || testResultsMissing) && testEvidencePersistencePort != null) {
                testEvidencePersistencePort.deleteTestEvidenceByTicketId(ticket.ticketId());
            }
            read++;
            written++;
        }

        boolean testPlanExists = snapshotsByFileName.containsKey("test-plan.md")
                && snapshotsByFileName.get("test-plan.md").existsFlag();
        boolean testResultsExists = snapshotsByFileName.containsKey("test-results.md")
                && snapshotsByFileName.get("test-results.md").existsFlag();
        boolean forceParseBothTestFiles = testPlanExists && testResultsExists &&
                ((snapshotsByFileName.get("test-plan.md").needParse())
                        || (snapshotsByFileName.get("test-results.md").needParse()));
        boolean cleanupDone = false;

        for (String fileName : CHANGE_TARGET_FILES) {
            ArtifactSnapshot snapshot = snapshotsByFileName.get(fileName);
            if (snapshot == null || !snapshot.existsFlag()) {
                continue;
            }
            String sourcePath = ticketDirPrefix + fileName;
            ArtifactScannerSourcePort.GitHubTreeEntry entry = tree.get(sourcePath);
            String artifactFileName = fileName;
            boolean isTestFile = "test-plan.md".equalsIgnoreCase(artifactFileName)
                    || "test-results.md".equalsIgnoreCase(artifactFileName);
            boolean shouldParse = snapshot.needParse() || (isTestFile && forceParseBothTestFiles);
            if (!shouldParse) {
                continue;
            }
            if (isTestFile && forceParseBothTestFiles && !cleanupDone && testEvidencePersistencePort != null) {
                testEvidencePersistencePort.deleteTestEvidenceByTicketId(ticket.ticketId());
                cleanupDone = true;
            }
            if ("spec-pack.md".equalsIgnoreCase(artifactFileName)) {
                try {
                    byte[] blob = source.readBlob(repoFullName, entry.sha());
                    String content = new String(blob, StandardCharsets.UTF_8);
                    var parsed = specPackParser.parse(content, sourcePath, "draft");
                    persistSpecPackParse(snapshot, ticket.ticketId(), projectId, repositoryId, runId, sourcePath,
                            parsed);
                } catch (RuntimeException ex) {
                    log.warn("Spec-pack parser failed for {}: {}", sourcePath, ex.getMessage());
                    persistParseFailure(snapshot, ticket.ticketId(), repositoryId, runId, "SPEC_PACK_PARSE",
                            ex.getMessage());
                }
            } else if ("self-review.md".equalsIgnoreCase(artifactFileName)) {
                try {
                    byte[] blob = source.readBlob(repoFullName, entry.sha());
                    String content = new String(blob, StandardCharsets.UTF_8);
                    var parsed = selfReviewParser.parse(content, sourcePath, "draft");
                    persistSelfReviewParse(snapshot, ticket.ticketId(), projectId, repositoryId, runId,
                            sourcePath, parsed);
                } catch (RuntimeException ex) {
                    log.warn("Self-review parser failed for {}: {}", sourcePath, ex.getMessage());
                    persistParseFailure(snapshot, ticket.ticketId(), repositoryId, runId, "SELF_REVIEW_PARSE",
                            ex.getMessage());
                }
            } else if ("impl-plan.md".equalsIgnoreCase(artifactFileName)) {
                parseDocArtifact(snapshot, sourcePath, repoFullName, entry, ticket.ticketId(), projectId,
                        repositoryId, runId, traceId, implPlanParser, "impl-plan-parser");
            } else if ("test-plan.md".equalsIgnoreCase(artifactFileName)) {
                parseDocArtifact(snapshot, sourcePath, repoFullName, entry, ticket.ticketId(), projectId,
                        repositoryId, runId, traceId, testPlanParser, "test-plan-parser");
            } else if ("test-results.md".equalsIgnoreCase(artifactFileName)) {
                parseDocArtifact(snapshot, sourcePath, repoFullName, entry, ticket.ticketId(), projectId,
                        repositoryId, runId, traceId, testResultsParser, "test-results-parser");
            } else if ("review-checklist.md".equalsIgnoreCase(artifactFileName)) {
                try {
                    byte[] blob = source.readBlob(repoFullName, entry.sha());
                    String content = new String(blob, StandardCharsets.UTF_8);
                    var parsed = reviewChecklistParser.parse(content, sourcePath, "draft");
                    persistReviewChecklistParse(snapshot, ticket.ticketId(), projectId, repositoryId, runId, sourcePath,
                            parsed);
                } catch (RuntimeException ex) {
                    log.warn("Review-checklist parser failed for {}: {}", sourcePath, ex.getMessage());
                    persistParseFailure(snapshot, ticket.ticketId(), repositoryId, runId, "REVIEW_CHECKLIST_PARSE",
                            ex.getMessage());
                }
            } else if ("report.md".equalsIgnoreCase(artifactFileName)) {
                try {
                    byte[] blob = source.readBlob(repoFullName, entry.sha());
                    String content = new String(blob, StandardCharsets.UTF_8);
                    var parsed = reportMarkdownParser.parse(content, sourcePath, "draft");
                    persistReportParse(snapshot, ticket.ticketId(), projectId, repositoryId, runId, sourcePath, parsed);
                } catch (RuntimeException ex) {
                    log.warn("Report parser failed for {}: {}", sourcePath, ex.getMessage());
                    persistParseFailure(snapshot, ticket.ticketId(), repositoryId, runId, "REPORT_PARSE",
                            ex.getMessage());
                }
            }
        }

        if (artifactDocumentDateService != null) {
            try {
                var documentDates = artifactDocumentDateService.extract(
                        repoFullName, ticket.ticketId().toString(), snapshotsByFileName, tree, ticketDirPrefix);
                persistence.upsertArtifactDocumentDates(documentDates);
            } catch (RuntimeException ex) {
                log.warn("ArtifactDocumentDateService failed for ticket {}: {}", ticket.ticketId(), ex.getMessage());
            }
        }

        if (ticketPhaseEvaluatorService != null) {
            ticketPhaseEvaluatorService.evaluateAndPersist(ticket.ticketId(), traceId);
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
                    "CI_PENDING",
                    runId,
                    snapshot.sourceUpdatedAt());
            if (parser instanceof ImplPlanParseService implPlanParseService) {
                implPlanParseService.parseAndStore(request);
            } else if (parser instanceof TestPlanParseService testPlanParseService) {
                testPlanParseService.parseAndStore(request);
            } else if (parser instanceof TestResultsParseService testResultsParseService) {
                testResultsParseService.parseAndStore(request);
            }
        } catch (RuntimeException ex) {
            log.warn("{} parser failed for {}: {}", parserName, sourcePath, ex.getMessage());
            persistParseFailure(snapshot, ticketId, repositoryId, runId,
                    parserName.toUpperCase(Locale.ROOT).replace("-", "_"), ex.getMessage());
        }
    }

    private void persistSpecPackParse(ArtifactSnapshot snapshot,
            UUID ticketId,
            UUID projectId,
            UUID repositoryId,
            UUID runId,
            String sourcePath,
            SpecPackMarkdownParser.ParsedArtifact parsed) {
        log.info("Spec-pack parsed for {}: parseStatus={}, warnings={}, errors={}", sourcePath, parsed.parseStatus(),
                parsed.warnings().size(), parsed.errors().size());
        log.debug("Spec-pack parse details for {}: required_fields_missing={}, ac_count={}, section_count={}",
                sourcePath, parsed.requiredFieldsMissing().size(), parsed.acceptanceCriteria().size(),
                parsed.sections().size());

        try {
            OffsetDateTime startedAt = HeaderDateNormalizer.normalize(parsed.headerMetadata().get("create_date"));
            persistence.updateTicketStartedAt(ticketId, startedAt);
        } catch (RuntimeException e) {
            log.warn("Failed to normalize/persist spec-pack created date for ticket {} at {}: {}", ticketId,
                    sourcePath, e.getMessage());
        }

        persistence.updateSnapshotParsedSummary(new ArtifactScannerModels.ParsedSummaryPatch(
                snapshot.artifactSnapshotId(),
                parsed.parsedSummary(),
                parsed.requiredFieldsMissing(),
                parsed.parserVersion(),
                parsed.errors().isEmpty()));

        persistence.deactivateAcceptanceCriteriaByTicketId(ticketId);
        persistence.deleteParsedSectionsByTicketIdAndSectionType(ticketId, "spec-pack");
        for (var ac : parsed.acceptanceCriteria()) {
            persistence.insertParsedAcceptanceCriteria(new ArtifactScannerModels.ParsedAcceptanceCriteria(
                    snapshot.artifactSnapshotId(),
                    ticketId,
                    ac.id(),
                    ac.description(),
                    computeSha256(ac.description()),
                    !ac.validFormat()));
        }

        var requiredSpecPackKeys = SpecPackMarkdownParser.requiredSectionKeys().stream()
                .map(this::normalizeSectionType)
                .collect(Collectors.toSet());

        for (String requiredKey : requiredSpecPackKeys) {
            String sectionText = parsed.sections().get(requiredKey);
            boolean present = SpecPackMarkdownParser.isParentSection(requiredKey)
                    ? parsed.sections().containsKey(requiredKey)
                    : sectionText != null && !sectionText.trim().isEmpty();
            persistence.insertParsedSection(new ArtifactScannerModels.ParsedSection(
                    snapshot.artifactSnapshotId(),
                    ticketId,
                    "spec-pack",
                    canonicalKeyToLowercaseSnakeCase(requiredKey),
                    sectionText != null && sectionText.length() > 500 ? sectionText.substring(0, 500) : sectionText,
                    computeSha256(sectionText != null ? sectionText : ""),
                    true,
                    present,
                    present && !parsed.requiredFieldsMissing().contains("section:" + requiredKey),
                    null));
        }

        for (var entrySection : parsed.sections().entrySet()) {
            String sectionKey = entrySection.getKey();
            String normalizedKey = normalizeSectionType(sectionKey);
            if (requiredSpecPackKeys.contains(normalizedKey)) {
                continue;
            }
            String sectionText = entrySection.getValue();
            if (sectionText == null || sectionText.trim().isEmpty()) {
                continue;
            }
            persistence.insertParsedSection(new ArtifactScannerModels.ParsedSection(
                    snapshot.artifactSnapshotId(),
                    ticketId,
                    "spec-pack",
                    canonicalKeyToLowercaseSnakeCase(sectionKey),
                    sectionText != null && sectionText.length() > 500 ? sectionText.substring(0, 500) : sectionText,
                    computeSha256(sectionText != null ? sectionText : ""),
                    false,
                    true,
                    parsed.errors().isEmpty(),
                    null));
        }

        persistParsedIssues(snapshot, ticketId, repositoryId, sourcePath, parsed, SPEC_PACK_SOURCE_TYPE);

        persistence.insertEvidenceEvent(new ArtifactScannerModels.EvidenceEvent(
                snapshot.artifactSnapshotId(),
                ticketId,
                repositoryId,
                runId,
                "SPEC_PACK_PARSE",
                "PARSE_COMPLETED",
                parsed.errors().isEmpty() ? "SUCCESS" : "PARTIAL",
                "Spec-pack parsed: " + parsed.parseStatus(),
                String.format(Locale.ROOT, "{\"parseStatus\":\"%s\",\"warnings\":%d,\"errors\":%d}",
                        parsed.parseStatus(), parsed.warnings().size(), parsed.errors().size())));

        persistence.insertDataQualityRecord(new ArtifactScannerModels.DataQualityRecord(
                runId,
                projectId,
                repositoryId,
                "SPEC_PACK_PARSE",
                sourcePath,
                parsed.requiredFieldsMissing().size(),
                parsed.errors().size(),
                0,
                calculateFreshnessDelayMinutes(snapshot.sourceUpdatedAt()),
                String.join("; ", parsed.errors().stream().map(e -> e.message()).toList())));

        if (evidenceQualityScoreService != null && ticketId != null) {
            evidenceQualityScoreService.recalculateFromParser(ticketId, null, runId.toString());
        }
    }

    private void persistSelfReviewParse(ArtifactSnapshot snapshot,
            UUID ticketId,
            UUID projectId,
            UUID repositoryId,
            UUID runId,
            String sourcePath,
            SelfReviewMarkdownParser.ParsedArtifact parsed) {
        log.info("Self-review parsed for {}: parseStatus={}, warnings={}, errors={}", sourcePath, parsed.parseStatus(),
                parsed.warnings().size(), parsed.errors().size());
        log.debug(
                "Self-review parse details for {}: required_sections_missing={}, table_count={}, section_count={}, final_verdict={}",
                sourcePath, parsed.requiredSectionsMissing().size(), parsed.tables().size(), parsed.sections().size(),
                parsed.finalVerdict());

        persistence.updateSnapshotParsedSummary(new ArtifactScannerModels.ParsedSummaryPatch(
                snapshot.artifactSnapshotId(),
                parsed.parsedSummary(),
                parsed.requiredSectionsMissing(),
                parsed.parserVersion(),
                parsed.errors().isEmpty()));

        persistence.deleteParsedSectionsByTicketIdAndSectionType(ticketId, "self-review");

        Set<String> persistedRequiredKeys = new LinkedHashSet<>();
        for (var section : parsed.sections()) {
            String sectionKey = section.canonicalKey();
            String sectionText = section.body();
            boolean present = isPresentSelfReviewSection(sectionKey, sectionText, parsed);
            boolean required = isRequiredSelfReviewSection(sectionKey);
            if (required) {
                persistedRequiredKeys.add(normalizeSectionType(sectionKey));
            }
            persistence.insertParsedSection(new ArtifactScannerModels.ParsedSection(
                    snapshot.artifactSnapshotId(),
                    ticketId,
                    "self-review",
                    canonicalKeyToLowercaseSnakeCase(sectionKey),
                    sectionText != null && sectionText.length() > 500 ? sectionText.substring(0, 500) : sectionText,
                    computeSha256(sectionText != null ? sectionText : ""),
                    required,
                    present,
                    parsed.errors().isEmpty() && present,
                    null));
        }

        for (String requiredKey : REQUIRED_SELF_REVIEW_SECTIONS) {
            if (!persistedRequiredKeys.contains(requiredKey)) {
                persistence.insertParsedSection(new ArtifactScannerModels.ParsedSection(
                        snapshot.artifactSnapshotId(),
                        ticketId,
                        "self-review",
                        canonicalKeyToLowercaseSnakeCase(requiredKey),
                        "",
                        computeSha256(""),
                        true,
                        false,
                        false,
                        null));
            }
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
                String.format(Locale.ROOT,
                        "{\"parseStatus\":\"%s\",\"warnings\":%d,\"errors\":%d,\"finalVerdict\":\"%s\"}",
                        parsed.parseStatus(), parsed.warnings().size(), parsed.errors().size(),
                        String.valueOf(parsed.finalVerdict()))));

        if (!parsed.requiredSectionsMissing().isEmpty() || !parsed.errors().isEmpty()) {
            persistence.insertDataQualityRecord(new ArtifactScannerModels.DataQualityRecord(
                    runId,
                    projectId,
                    repositoryId,
                    "SELF_REVIEW_PARSE",
                    sourcePath,
                    parsed.requiredSectionsMissing().size(),
                    parsed.errors().size(),
                    0,
                    calculateFreshnessDelayMinutes(snapshot.sourceUpdatedAt()),
                    String.join("; ", parsed.errors().stream().map(e -> e.message()).toList())));
        }

        // AC-FCI-4/5: persist explicit exception records from the EXCEPTION_RECORD
        // section
        persistExceptionRecords(snapshot, ticketId, repositoryId, sourcePath, parsed.exceptionRecords(), "self-review");

        if (evidenceQualityScoreService != null && ticketId != null) {
            evidenceQualityScoreService.recalculateFromParser(ticketId, null, runId.toString());
        }
    }

    private void persistExceptionRecords(ArtifactSnapshot snapshot,
            UUID ticketId,
            UUID repositoryId,
            String sourcePath,
            List<com.sdd.platform.domain.service.markdown.selfreview.SelfReviewMarkdownParser.ParsedExceptionRecord> exRecords,
            String sourceSection) {
        if (exRecords == null || exRecords.isEmpty()) {
            // AC-FCI-6: no explicit rows � warn only, do not create synthetic rows
            log.warn("No EXCEPTION_RECORD rows found in {} for ticket {}", sourceSection, ticketId);
            return;
        }
        List<ParsedException> toUpsert = exRecords.stream()
                .map(rec -> new ParsedException(
                        snapshot.artifactSnapshotId(),
                        ticketId,
                        repositoryId,
                        rec.exceptionType(),
                        rec.reasonPresent(),
                        rec.reason(),
                        rec.alternativeCheck(),
                        rec.approved(),
                        rec.approvedByRoleName(),
                        rec.expiryDate(),
                        rec.followUpStatus() != null ? rec.followUpStatus() : "OPEN",
                        (rec.status() != null && !rec.status().isBlank()) ? rec.status() : null,
                        sourcePath,
                        sourceSection))
                .toList();
        persistence.upsertParsedExceptions(toUpsert);
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
                    "{}"));
        } catch (Exception eventEx) {
            log.debug("Failed to log parse failure event", eventEx);
        }
    }

    private void persistReviewChecklistParse(
            ArtifactSnapshot snapshot,
            UUID ticketId,
            UUID projectId,
            UUID repositoryId,
            UUID runId,
            String sourcePath,
            ReviewChecklistMarkdownParser.ParsedArtifact parsed) {

        log.info("Review-checklist parsed for {}: parseStatus={}, warnings={}, errors={}",
                sourcePath,
                parsed.parseStatus(),
                parsed.warnings().size(),
                parsed.errors().size());

        log.debug("Review-checklist details for {}: required_fields_missing={}, section_count={}",
                sourcePath,
                parsed.requiredFieldsMissing().size(),
                parsed.sections().size());

        // 1. update snapshot
        persistence.updateSnapshotParsedSummary(
                new ArtifactScannerModels.ParsedSummaryPatch(
                        snapshot.artifactSnapshotId(),
                        parsed.parsedSummary(),
                        parsed.requiredFieldsMissing(),
                        parsed.parserVersion(),
                        parsed.errors().isEmpty()));

        // 2. clear stale sections then re-insert
        persistence.deleteParsedSectionsByTicketIdAndSectionType(ticketId, "review-checklist");
        var requiredKeys = ReviewChecklistMarkdownParser.REQUIRED_SECTION_KEYS.stream()
                .collect(Collectors.toSet());
        for (String requiredKey : requiredKeys) {
            String sectionText = parsed.sections().get(requiredKey);
            boolean present = sectionText != null && !sectionText.isBlank();
            boolean headingOnlyPresent = !present && ReviewChecklistMarkdownParser.isHeadingOnlySection(requiredKey)
                    && parsed.sections().containsKey(requiredKey);
            boolean valid = (present || headingOnlyPresent) && (!isPlaceholderToken(sectionText) || headingOnlyPresent);
            boolean effectivePresent = present || headingOnlyPresent;

            persistence.insertParsedSection(
                    new ArtifactScannerModels.ParsedSection(
                            snapshot.artifactSnapshotId(),
                            ticketId,
                            "review-checklist",
                            canonicalKeyToLowercaseSnakeCase(requiredKey),
                            sectionText != null && sectionText.length() > 500 ? sectionText.substring(0, 500)
                                    : sectionText,
                            computeSha256(sectionText != null ? sectionText : ""),
                            true,
                            effectivePresent,
                            valid,
                            null));
        }
        for (var entry : parsed.sections().entrySet()) {
            String key = entry.getKey();
            if (requiredKeys.contains(key)) {
                continue;
            }
            String value = entry.getValue();
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            boolean present = value != null && !value.isBlank();
            boolean headingOnlyPresent = !present && ReviewChecklistMarkdownParser.isHeadingOnlySection(key)
                    && parsed.sections().containsKey(key);
            boolean valid = (present || headingOnlyPresent) && (!isPlaceholderToken(value) || headingOnlyPresent);

            persistence.insertParsedSection(
                    new ArtifactScannerModels.ParsedSection(
                            snapshot.artifactSnapshotId(),
                            ticketId,
                            "review-checklist",
                            canonicalKeyToLowercaseSnakeCase(key),
                            value != null && value.length() > 500 ? value.substring(0, 500) : value,
                            computeSha256(value != null ? value : ""),
                            false,
                            true,
                            valid,
                            null));
        }

        persistence.insertEvidenceEvent(
                new ArtifactScannerModels.EvidenceEvent(
                        snapshot.artifactSnapshotId(),
                        ticketId,
                        repositoryId,
                        runId,
                        "REVIEW_CHECKLIST_PARSE",
                        "PARSE_COMPLETED",
                        parsed.errors().isEmpty() ? "SUCCESS" : "PARTIAL",
                        "Review-checklist parsed: " + parsed.parseStatus(),
                        String.format(
                                Locale.ROOT,
                                "{\"parseStatus\":\"%s\",\"warnings\":%d,\"errors\":%d}",
                                parsed.parseStatus(),
                                parsed.warnings().size(),
                                parsed.errors().size())));

        // 4. data quality record when issues exist
        persistence.insertDataQualityRecord(new ArtifactScannerModels.DataQualityRecord(
                runId,
                projectId,
                repositoryId,
                "REVIEW_CHECKLIST_PARSE",
                sourcePath,
                parsed.requiredFieldsMissing().size(),
                parsed.errors().size(),
                0,
                calculateFreshnessDelayMinutes(snapshot.sourceUpdatedAt()),
                String.join("; ", parsed.errors().stream().map(e -> e.message()).toList())));

        // 5. trigger quality score recalculation
        if (evidenceQualityScoreService != null && ticketId != null) {
            evidenceQualityScoreService.recalculateFromParser(ticketId, null, runId.toString());
        }
    }

    private void persistReportParse(
            ArtifactSnapshot snapshot,
            UUID ticketId,
            UUID projectId,
            UUID repositoryId,
            UUID runId,
            String sourcePath,
            ReportMarkdownParser.ParsedArtifact parsed) {

        log.info("Report parsed for {}: parseStatus={}, warnings={}, errors={}",
                sourcePath, parsed.parseStatus(), parsed.warnings().size(), parsed.errors().size());
        log.debug("Report parse details for {}: required_fields_missing={}, section_count={}",
                sourcePath, parsed.requiredFieldsMissing().size(), parsed.sections().size());

        try {
            OffsetDateTime completedAt = HeaderDateNormalizer.normalize(parsed.headerMetadata().get("update_date"));
            persistence.updateTicketCompletedAt(ticketId, completedAt);
        } catch (RuntimeException e) {
            log.warn("Failed to normalize/persist report updated date for ticket {} at {}: {}", ticketId,
                    sourcePath, e.getMessage());
        }

        // 1. update snapshot
        persistence.updateSnapshotParsedSummary(
                new ArtifactScannerModels.ParsedSummaryPatch(
                        snapshot.artifactSnapshotId(),
                        parsed.parsedSummary(),
                        parsed.requiredFieldsMissing(),
                        parsed.parserVersion(),
                        parsed.errors().isEmpty()));

        // 2. clear stale sections then re-insert
        persistence.deleteParsedSectionsByTicketIdAndSectionType(ticketId, "report");
        persistence.deleteParsedRisksByTicketId(ticketId);

        var requiredReportKeys = ReportMarkdownParser.ALL_FIELDS.stream()
                .collect(Collectors.toSet());

        for (String requiredKey : requiredReportKeys) {
            String sectionText = parsed.sections().get(requiredKey);
            boolean present = sectionText != null && !sectionText.trim().isEmpty();
            boolean valid = present
                    && !parsed.requiredFieldsMissing().contains("section:" + requiredKey.toLowerCase(Locale.ROOT));

            persistence.insertParsedSection(
                    new ArtifactScannerModels.ParsedSection(
                            snapshot.artifactSnapshotId(),
                            ticketId,
                            "report",
                            canonicalKeyToLowercaseSnakeCase(requiredKey),
                            sectionText != null && sectionText.length() > 500 ? sectionText.substring(0, 500)
                                    : sectionText,
                            computeSha256(sectionText != null ? sectionText : ""),
                            true,
                            present,
                            valid,
                            null));
        }

        for (var entry : parsed.sections().entrySet()) {
            String sectionKey = entry.getKey();
            if (requiredReportKeys.contains(sectionKey)) {
                continue;
            }
            String sectionText = entry.getValue();
            if (sectionText == null || sectionText.trim().isEmpty()) {
                continue;
            }
            persistence.insertParsedSection(
                    new ArtifactScannerModels.ParsedSection(
                            snapshot.artifactSnapshotId(),
                            ticketId,
                            "report",
                            canonicalKeyToLowercaseSnakeCase(sectionKey),
                            sectionText != null && sectionText.length() > 500 ? sectionText.substring(0, 500)
                                    : sectionText,
                            computeSha256(sectionText != null ? sectionText : ""),
                            false,
                            true,
                            !isPlaceholderToken(sectionText),
                            null));
        }

        for (AcceptedRiskRow acceptedRisk : parsed.acceptedRisks()) {
            String status = acceptedRisk.status() == null ? "OPEN"
                    : acceptedRisk.status().trim().toUpperCase(Locale.ROOT);
            String riskKey = computeStableHash(String.join("|",
                    nullToEmpty(acceptedRisk.risk()),
                    nullToEmpty(acceptedRisk.impact()),
                    nullToEmpty(acceptedRisk.owner()),
                    nullToEmpty(acceptedRisk.deadline()),
                    nullToEmpty(status),
                    nullToEmpty(acceptedRisk.approver())));
            String riskSummary = buildAcceptedRiskSummary(acceptedRisk);
            String mitigationSummary = buildAcceptedRiskMitigationSummary(acceptedRisk);
            String severity = deriveRiskSeverity(acceptedRisk.impact());

            persistence.insertParsedRisk(new ArtifactScannerModels.ParsedRisk(
                    snapshot.artifactSnapshotId(),
                    ticketId,
                    repositoryId,
                    riskKey,
                    riskSummary,
                    severity,
                    false,
                    mitigationSummary,
                    status));
        }

        // 3. evidence event
        persistence.insertEvidenceEvent(
                new ArtifactScannerModels.EvidenceEvent(
                        snapshot.artifactSnapshotId(),
                        ticketId,
                        repositoryId,
                        runId,
                        "REPORT_PARSE",
                        "PARSE_COMPLETED",
                        parsed.errors().isEmpty() ? "SUCCESS" : "PARTIAL",
                        "Report parsed: " + parsed.parseStatus(),
                        String.format(Locale.ROOT, "{\"parseStatus\":\"%s\",\"warnings\":%d,\"errors\":%d}",
                                parsed.parseStatus(), parsed.warnings().size(), parsed.errors().size())));

        persistParsedIssues(snapshot, ticketId, repositoryId, sourcePath, parsed, REPORT_SOURCE_TYPE);

        // 4. data quality record when issues exist
        persistence.insertDataQualityRecord(new ArtifactScannerModels.DataQualityRecord(
                runId,
                projectId,
                repositoryId,
                "REPORT_PARSE",
                sourcePath,
                parsed.requiredFieldsMissing().size(),
                parsed.errors().size(),
                0,
                calculateFreshnessDelayMinutes(snapshot.sourceUpdatedAt()),
                String.join("; ", parsed.errors().stream().map(e -> e.message()).toList())));

        // 5. trigger quality score recalculation
        if (evidenceQualityScoreService != null && ticketId != null) {
            evidenceQualityScoreService.recalculateFromParser(ticketId, null, runId.toString());
        }
    }

    private void persistParsedIssues(ArtifactSnapshot snapshot,
            UUID ticketId,
            UUID repositoryId,
            String sourcePath,
            SpecPackMarkdownParser.ParsedArtifact parsed,
            String sourceType) {
        if (ticketId == null) {
            return;
        }
        List<ParsedIssue> issues = extractSpecPackIssues(snapshot, ticketId, repositoryId, sourcePath, parsed,
                sourceType);
        persistence.deleteParsedIssuesByTicketIdAndSourceType(ticketId, sourceType);
        for (ParsedIssue issue : issues) {
            persistence.insertParsedIssue(issue);
        }
    }

    private void persistParsedIssues(ArtifactSnapshot snapshot,
            UUID ticketId,
            UUID repositoryId,
            String sourcePath,
            ReportMarkdownParser.ParsedArtifact parsed,
            String sourceType) {
        if (ticketId == null) {
            return;
        }
        List<ParsedIssue> issues = extractReportIssues(snapshot, ticketId, repositoryId, sourcePath, parsed,
                sourceType);
        persistence.deleteParsedIssuesByTicketIdAndSourceType(ticketId, sourceType);
        for (ParsedIssue issue : issues) {
            persistence.insertParsedIssue(issue);
        }
    }

    private List<ParsedIssue> extractSpecPackIssues(ArtifactSnapshot snapshot,
            UUID ticketId,
            UUID repositoryId,
            String sourcePath,
            SpecPackMarkdownParser.ParsedArtifact parsed,
            String sourceType) {
        List<ParsedIssue> issues = new ArrayList<>();
        for (MarkdownTable table : parsed.tables()) {
            if (!OPEN_ISSUES_SECTION_KEY.equals(normalizeSectionType(table.sectionKey()))) {
                continue;
            }
            issues.addAll(mapOpenIssueTableRows(snapshot, ticketId, repositoryId, sourcePath, sourceType, table));
        }
        return issues;
    }

    private List<ParsedIssue> extractReportIssues(ArtifactSnapshot snapshot,
            UUID ticketId,
            UUID repositoryId,
            String sourcePath,
            ReportMarkdownParser.ParsedArtifact parsed,
            String sourceType) {
        List<ParsedIssue> issues = new ArrayList<>();
        for (MarkdownTable table : parsed.tables()) {
            if (!OPEN_ISSUES_SECTION_KEY.equals(normalizeSectionType(table.sectionKey()))) {
                continue;
            }
            issues.addAll(mapOpenIssueTableRows(snapshot, ticketId, repositoryId, sourcePath, sourceType, table));
        }

        if (!issues.isEmpty()) {
            return issues;
        }

        String sectionText = findSectionText(parsed.sections(), OPEN_ISSUES_SECTION_KEY);
        if (sectionText == null || sectionText.isBlank() || isNoOpenIssuesText(sectionText)) {
            return issues;
        }

        String[] lines = sectionText.split("\\R");
        int order = 0;
        for (String line : lines) {
            String issueText = normalizeIssueTextLine(line);
            if (issueText == null) {
                continue;
            }
            order++;
            issues.add(new ParsedIssue(
                    snapshot.artifactSnapshotId(),
                    ticketId,
                    repositoryId,
                    sourceType,
                    order,
                    null,
                    issueText,
                    null,
                    null,
                    null,
                    issueText,
                    sourcePath,
                    snapshot.collectedAt()));
        }
        return issues;
    }

    private List<ParsedIssue> mapOpenIssueTableRows(ArtifactSnapshot snapshot,
            UUID ticketId,
            UUID repositoryId,
            String sourcePath,
            String sourceType,
            MarkdownTable table) {
        List<ParsedIssue> issues = new ArrayList<>();
        if (table == null || table.rows() == null || table.rows().isEmpty()) {
            return issues;
        }
        int issueIndex = 0;
        for (List<String> row : table.rows()) {
            String issueKey = cell(row, findHeaderIndex(table.headers(), "id", "issue id", "key"));
            String issueTitle = firstNonBlank(
                    cell(row, findHeaderIndex(table.headers(), "issue", "title", "summary", "description")),
                    cell(row, 1));
            String issueImpact = firstNonBlank(
                    cell(row, findHeaderIndex(table.headers(), "impact", "severity")),
                    cell(row, 2));
            String issueOwner = firstNonBlank(
                    cell(row, findHeaderIndex(table.headers(), "owner", "assignee", "responsible")),
                    cell(row, 3));
            String issueStatus = firstNonBlank(
                    cell(row, findHeaderIndex(table.headers(), "status", "state")),
                    cell(row, 4));
            if (issueStatus == null && REPORT_SOURCE_TYPE.equals(sourceType)) {
                issueStatus = "Open";
            }
            String issueSummary = buildIssueSummary(issueKey, issueTitle, issueImpact, issueOwner, issueStatus, row);
            if (issueSummary.isBlank()) {
                continue;
            }
            issueIndex++;
            issues.add(new ParsedIssue(
                    snapshot.artifactSnapshotId(),
                    ticketId,
                    repositoryId,
                    sourceType,
                    issueIndex,
                    blankToNull(issueKey),
                    blankToNull(issueTitle),
                    blankToNull(issueImpact),
                    blankToNull(issueOwner),
                    blankToNull(issueStatus),
                    issueSummary,
                    sourcePath,
                    snapshot.collectedAt()));
        }
        return issues;
    }

    private int findHeaderIndex(List<String> headers, String... aliases) {
        if (headers == null || headers.isEmpty() || aliases == null || aliases.length == 0) {
            return -1;
        }
        for (int i = 0; i < headers.size(); i++) {
            String normalizedHeader = normalizeHeader(headers.get(i));
            for (String alias : aliases) {
                if (normalizedHeader.equals(normalizeHeader(alias))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private String cell(List<String> row, int index) {
        if (row == null || index < 0 || index >= row.size()) {
            return null;
        }
        String value = row.get(index);
        return value == null ? null : value.trim();
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

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String buildIssueSummary(String issueKey,
            String issueTitle,
            String issueImpact,
            String issueOwner,
            String issueStatus,
            List<String> row) {
        List<String> parts = new ArrayList<>();
        if (issueKey != null && !issueKey.isBlank()) {
            parts.add(issueKey.trim());
        }
        if (issueTitle != null && !issueTitle.isBlank()) {
            parts.add(issueTitle.trim());
        }
        if (issueImpact != null && !issueImpact.isBlank()) {
            parts.add("impact=" + issueImpact.trim());
        }
        if (issueOwner != null && !issueOwner.isBlank()) {
            parts.add("owner=" + issueOwner.trim());
        }
        if (issueStatus != null && !issueStatus.isBlank()) {
            parts.add("status=" + issueStatus.trim());
        }
        if (!parts.isEmpty()) {
            return String.join(" | ", parts);
        }
        if (row == null || row.isEmpty()) {
            return "";
        }
        return row.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .reduce((left, right) -> left + " | " + right)
                .orElse("");
    }

    private String findSectionText(Map<String, String> sections, String normalizedSectionKey) {
        if (sections == null || sections.isEmpty() || normalizedSectionKey == null || normalizedSectionKey.isBlank()) {
            return null;
        }
        for (Map.Entry<String, String> entry : sections.entrySet()) {
            if (normalizedSectionKey.equals(normalizeSectionType(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean isNoOpenIssuesText(String sectionText) {
        if (sectionText == null) {
            return false;
        }
        return sectionText.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .anyMatch(line -> line.toLowerCase(Locale.ROOT).startsWith("no open issue"));
    }

    private String normalizeIssueTextLine(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isBlank() || isNoOpenIssuesText(trimmed)) {
            return null;
        }
        String cleaned = trimmed
                .replaceFirst("^[-*+]\\s+", "")
                .replaceFirst("^\\d+[.)]\\s+", "")
                .trim();
        if (cleaned.isBlank()) {
            return null;
        }
        if (cleaned.equalsIgnoreCase("No open issues remaining.")) {
            return null;
        }
        return cleaned;
    }

    private String buildAcceptedRiskSummary(AcceptedRiskRow acceptedRisk) {
        return String.format(Locale.ROOT,
                "risk=%s | impact=%s | owner=%s | deadline=%s | status=%s | approver=%s",
                nullToEmpty(acceptedRisk.risk()),
                nullToEmpty(acceptedRisk.impact()),
                nullToEmpty(acceptedRisk.owner()),
                nullToEmpty(acceptedRisk.deadline()),
                nullToEmpty(acceptedRisk.status()),
                nullToEmpty(acceptedRisk.approver()));
    }

    private String buildAcceptedRiskMitigationSummary(AcceptedRiskRow acceptedRisk) {
        return String.format(Locale.ROOT,
                "owner=%s | deadline=%s | approver=%s",
                nullToEmpty(acceptedRisk.owner()),
                nullToEmpty(acceptedRisk.deadline()),
                nullToEmpty(acceptedRisk.approver()));
    }

    private Integer calculateFreshnessDelayMinutes(OffsetDateTime sourceUpdatedAt) {
        if (sourceUpdatedAt == null) {
            return null;
        }
        long minutes = Duration.between(sourceUpdatedAt, OffsetDateTime.now(ZoneOffset.UTC)).toMinutes();
        return (int) Math.max(minutes, 0L);
    }

    private String deriveRiskSeverity(String impact) {
        String normalized = impact == null ? "" : impact.toLowerCase(Locale.ROOT);
        if (normalized.contains("critical")) {
            return "CRITICAL";
        }
        if (normalized.contains("high")) {
            return "HIGH";
        }
        if (normalized.contains("medium")) {
            return "MEDIUM";
        }
        if (normalized.contains("low")) {
            return "LOW";
        }
        return "MEDIUM";
    }

    private String computeStableHash(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return input == null ? "" : input;
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
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
        if (sectionKey == null)
            return null;
        return sectionKey.toUpperCase().replace(" ", "_").replace("-", "_");
    }

    private String canonicalKeyToLowercaseSnakeCase(String key) {
        if (key == null)
            return null;
        return key.toLowerCase(Locale.ROOT);
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

    private boolean isPresentSelfReviewSection(String sectionKey, String sectionText,
            SelfReviewMarkdownParser.ParsedArtifact parsed) {
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
            "ACCEPTANCE_CRITERIA", "EXAMPLES", "HUMAN_DECISION_REQUIRED", "OPEN_ISSUES");

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
            "FINAL_SELF_VERDICT");

    private static final Set<String> SELF_REVIEW_TABLE_SECTIONS = Set.of(
            "SPECIFICATION_AC_MATCHING",
            "LIST_OF_CHANGED_FILES",
            "RUN_COMMAND_AND_RESULTS",
            "SELF_CHECK_USING_REVIEW_CHECKLIST",
            "BUGS_FOUND_AND_RESOLVED",
            "UNPROCESSED_PENDING_ACCEPTED_RISK",
            "EXCEPTION_RECORD");

    private ScanCounts scanPhase0(UUID runId,
            UUID repositoryId,
            UUID projectId,
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

            var previousOpt = persistence.findLatestSnapshot(repositoryId, sourcePath, artifactType.artifactTypeId());
            if (previousOpt.isPresent()) {
                ArtifactSnapshot previous = previousOpt.get();
                persistence.updateSnapshot(snapshot, previous.artifactSnapshotId());
            } else {
                persistence.insertSnapshot(snapshot);
            }
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
        Integer schemaVersion = null;

        if (exists) {
            try {
                byte[] bytes = source.readBlob(repoFullName, entry.sha());
                sizeBytes = entry.size() != null ? entry.size() : (long) bytes.length;
                contentHash = sha256(bytes);
                sourceUpdatedAt = revision.committedAt();
                templateEmpty = bytes.length == 0 || new String(bytes, StandardCharsets.UTF_8).trim().isEmpty();
                ArtifactSnapshot previous = persistence
                        .findLatestSnapshot(repositoryId, sourcePath, artifactType.artifactTypeId()).orElse(null);
                needParse = previous == null || !Objects.equals(previous.contentHash(), contentHash);
                schemaVersion = resolveSchemaVersion(previous, contentHash);
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
                schemaVersion,
                sizeBytes,
                sourceUpdatedAt,
                templateEmpty,
                needParse,
                scanStatus,
                scanMessage,
                now);
    }

    private Integer resolveSchemaVersion(ArtifactSnapshot previous, String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return null;
        }
        if (previous == null || previous.schemaVersion() == null) {
            return 1;
        }
        if (Objects.equals(previous.contentHash(), contentHash)) {
            return previous.schemaVersion();
        }
        return previous.schemaVersion() + 1;
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
        if (end <= start)
            return null;
        String key = path.substring(start, end).trim();
        return key.isBlank() ? null : key;
    }

    private String resolveTicketDirPrefix(Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree,
            String ticketKey) {
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

    private static String resolvePhase0FilePath(Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree,
            String fileName) {
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

    private record ScanCounts(int read, int written) {
    }
}
