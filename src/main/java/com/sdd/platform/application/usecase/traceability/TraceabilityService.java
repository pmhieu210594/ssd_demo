package com.sdd.platform.application.usecase.traceability;

import com.sdd.platform.application.port.out.persistence.TraceabilityRepositoryPort;
import com.sdd.platform.application.usecase.traceability.TraceabilityModels.TraceabilityReviewCommentRow;
import com.sdd.platform.domain.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.sdd.platform.application.usecase.traceability.TraceabilityModels.REQUIRED_ARTIFACT_CODES;

@Service
public class TraceabilityService {

    private static final Logger log = LoggerFactory.getLogger(TraceabilityService.class);
    private static final String REPORT_ARTIFACT_TYPE = "REPORT";
    private static final int EXPECTED_EVIDENCE_COUNT = REQUIRED_ARTIFACT_CODES.size() + 2;
    private static final Map<String, ReportSectionRule> REPORT_SECTION_RULES = createReportSectionRules();

    private final TraceabilityRepositoryPort repository;

    public TraceabilityService(TraceabilityRepositoryPort repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public TraceabilityModels.TraceabilityView getTraceability(UUID ticketId) {
        TraceabilityModels.TicketRow ticket = repository.findTicket(ticketId)
                .orElseThrow(() -> new NotFoundException("Pages.Traceability.NotFound"));

        log.info("Traceability view requested for ticketId={}, scope=read-only", ticketId);

        Map<String, TraceabilityModels.ArtifactCoverageRow> artifactsByCode = repository.findArtifacts(ticketId).stream()
                .filter(row -> row.artifactTypeCode() != null)
                .collect(Collectors.toMap(
                        row -> row.artifactTypeCode().toUpperCase(Locale.ROOT),
                        row -> row,
                        (left, right) -> preferLatest(left, right),
                        LinkedHashMap::new
                ));

        List<TraceabilityModels.ArtifactCoverage> artifacts = REQUIRED_ARTIFACT_CODES.stream()
                .map(code -> toArtifactCoverage(code, artifactsByCode.get(code)))
                .toList();

        List<TraceabilityModels.PullRequestCoverage> pullRequests = repository.findPullRequests(ticketId).stream()
                .map(this::toPullRequestCoverage)
                .toList();

        List<TraceabilityModels.CommitCoverage> commits = repository.findCommits(ticketId).stream()
                .map(this::toCommitCoverage)
                .toList();

        List<TraceabilityModels.CiRunCoverage> ciRuns = repository.findCiRuns(ticketId).stream()
                .map(this::toCiRunCoverage)
                .toList();

        List<TraceabilityModels.ParsedSectionRow> parsedSections = repository.findParsedSections(ticketId);

        List<TraceabilityModels.TraceabilityLink> links = repository.findTraceabilityLinks(ticketId).stream()
                .map(this::toTraceabilityLink)
                .sorted(Comparator
                        .comparing(TraceabilityModels.TraceabilityLink::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(link -> safeSortValue(link.traceabilityLinkId())))
                .toList();

        List<TraceabilityModels.TimelineEvent> timelineEvents = repository.findEvidenceEvents(ticketId).stream()
                .map(this::toTimelineEvent)
                .sorted(Comparator
                        .comparing(TraceabilityModels.TimelineEvent::eventTimestamp, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(event -> safeSortValue(event.eventId())))
                .toList();

        List<TraceabilityModels.BrokenLink> brokenLinks = buildBrokenLinks(artifacts, pullRequests, ciRuns, parsedSections);
        Set<String> brokenArtifactCodes = parsedSections.stream()
                .filter(this::isBrokenSection)
                .map(section -> section.artifactTypeCode().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        int artifactCount = (int) artifacts.stream()
                .filter(TraceabilityModels.ArtifactCoverage::existsFlag)
                .filter(artifact -> !brokenArtifactCodes.contains(artifact.artifactTypeCode().toUpperCase(Locale.ROOT)))
                .count();
        int prCount = pullRequests.isEmpty() ? 0 : 1;
        int ciCount = ciRuns.isEmpty() ? 0 : 1;
        int foundCount = artifactCount + prCount + ciCount;
        int completenessPercent = (int) Math.round(foundCount * 100.0 / EXPECTED_EVIDENCE_COUNT);
        int reviewRoundCount = repository.findReviewRoundCount(ticketId);
        List<TraceabilityReviewCommentRow> reviewComments = repository.findReviewComments(ticketId);

        TraceabilityModels.Summary summary = new TraceabilityModels.Summary(
                ticket.ticketId(),
                ticket.externalTicketKey(),
                ticket.title(),
                completenessPercent,
                foundCount,
                EXPECTED_EVIDENCE_COUNT,
                artifactCount,
                pullRequests.size(),
                commits.size(),
                ciRuns.size(),
                brokenLinks.size(),
                reviewRoundCount
        );

        return new TraceabilityModels.TraceabilityView(
                summary,
                artifacts,
                pullRequests,
                commits,
                ciRuns,
                links,
                brokenLinks,
                timelineEvents,
                reviewComments
        );
    }

    private TraceabilityModels.ArtifactCoverage toArtifactCoverage(String artifactTypeCode, TraceabilityModels.ArtifactCoverageRow row) {
        return new TraceabilityModels.ArtifactCoverage(
                row == null ? null : row.artifactSnapshotId(),
                artifactTypeCode,
                row == null ? defaultArtifactName(artifactTypeCode) : row.artifactName(),
                row == null ? defaultFileName(artifactTypeCode) : row.defaultFileName(),
                true,
                row == null ? null : row.sourcePath(),
                row != null && row.existsFlag(),
                row == null ? null : row.collectedAt(),
                row == null ? null : row.schemaVersion()
        );
    }

    private TraceabilityModels.PullRequestCoverage toPullRequestCoverage(TraceabilityModels.PullRequestCoverageRow row) {
        return new TraceabilityModels.PullRequestCoverage(
                row.prId(),
                row.externalPrId(),
                row.externalPrUrl(),
                row.title(),
                row.status(),
                row.sourceBranch(),
                row.targetBranch(),
                row.openedAt(),
                row.mergedAt(),
                row.closedAt(),
                row.collectedAt()
        );
    }

    private TraceabilityModels.CommitCoverage toCommitCoverage(TraceabilityModels.CommitCoverageRow row) {
        return new TraceabilityModels.CommitCoverage(
                row.commitId(),
                row.commitHash(),
                row.branchName(),
                row.messageHash(),
                row.commitUrl(),
                row.committedAt(),
                row.collectedAt()
        );
    }

    private TraceabilityModels.CiRunCoverage toCiRunCoverage(TraceabilityModels.CiRunCoverageRow row) {
        return new TraceabilityModels.CiRunCoverage(
                row.ciRunId(),
                row.externalCiRunId(),
                row.ciUrl(),
                row.workflowName(),
                row.status(),
                row.startedAt(),
                row.finishedAt(),
                row.collectedAt()
        );
    }

    private TraceabilityModels.TraceabilityLink toTraceabilityLink(TraceabilityModels.TraceabilityLinkRow row) {
        return new TraceabilityModels.TraceabilityLink(
                row.traceabilityLinkId(),
                row.ticketId(),
                row.sourceType(),
                row.sourceId(),
                row.targetType(),
                row.targetId(),
                row.confidence() == null ? 0.0 : row.confidence(),
                row.confidenceLevel(),
                row.ruleName(),
                row.evidenceJson(),
                row.createdAt()
        );
    }

    private TraceabilityModels.TimelineEvent toTimelineEvent(TraceabilityModels.EvidenceEventRow row) {
        return new TraceabilityModels.TimelineEvent(
                row.evidenceEventId(),
                row.eventType(),
                row.sourceType(),
                row.sourceRefId(),
                row.result(),
                row.summary(),
                row.eventTimestamp()
        );
    }

    private List<TraceabilityModels.BrokenLink> buildBrokenLinks(
            List<TraceabilityModels.ArtifactCoverage> artifacts,
            List<TraceabilityModels.PullRequestCoverage> pullRequests,
            List<TraceabilityModels.CiRunCoverage> ciRuns,
            List<TraceabilityModels.ParsedSectionRow> parsedSections
    ) {
        List<TraceabilityModels.BrokenLink> brokenLinks = new ArrayList<>();
        for (TraceabilityModels.ArtifactCoverage artifact : artifacts) {
            if (!artifact.existsFlag()) {
                brokenLinks.add(new TraceabilityModels.BrokenLink(
                        artifact.artifactTypeCode(),
                        artifact.artifactName(),
                        "WARNING",
                        artifact.artifactName() + " is missing for this ticket."
                ));
            }
        }
        brokenLinks.addAll(buildParsedSectionBrokenLinks(parsedSections));
        if (pullRequests.isEmpty()) {
            brokenLinks.add(new TraceabilityModels.BrokenLink(
                    "PR",
                    "Pull Request",
                    "ERROR",
                    "Pull request is missing for this ticket."
            ));
        }
        if (ciRuns.isEmpty()) {
            brokenLinks.add(new TraceabilityModels.BrokenLink(
                    "CI",
                    "CI Run",
                    "ERROR",
                    "CI run is missing for this ticket."
            ));
        }
        return brokenLinks;
    }

    private List<TraceabilityModels.BrokenLink> buildParsedSectionBrokenLinks(List<TraceabilityModels.ParsedSectionRow> parsedSections) {
        if (parsedSections == null || parsedSections.isEmpty()) {
            return List.of();
        }
        Map<String, List<TraceabilityModels.ParsedSectionRow>> grouped = parsedSections.stream()
                .filter(this::isBrokenSection)
                .collect(Collectors.groupingBy(
                        section -> section.artifactSnapshotId() == null
                                ? section.artifactTypeCode().toUpperCase(Locale.ROOT)
                                : section.artifactSnapshotId().toString(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<TraceabilityModels.BrokenLink> brokenLinks = new ArrayList<>();
        for (List<TraceabilityModels.ParsedSectionRow> sections : grouped.values()) {
            TraceabilityModels.ParsedSectionRow first = sections.getFirst();
            List<String> missingSections = sections.stream()
                    .map(this::sectionLabel)
                    .distinct()
                    .toList();
            brokenLinks.add(new TraceabilityModels.BrokenLink(
                    first.artifactTypeCode() + "-PARSER",
                    first.artifactName(),
                    "ERROR",
                    first.artifactName() + " is missing required section(s): " + String.join(", ", missingSections)
            ));
        }
        return brokenLinks;
    }

    private boolean isBrokenSection(TraceabilityModels.ParsedSectionRow section) {
        if (section == null) {
            return false;
        }
        if (resolveReportSectionRule(section)
                .map(ReportSectionRule::ignoreAsBrokenLink)
                .orElse(false)) {
            return false;
        }
        boolean consideredPresent = section.presentFlag()
                || (section.validFlag() != null && section.validFlag());
        if (section.requiredFlag() && !consideredPresent) {
            return true;
        }
        if (section.validFlag() != null && !section.validFlag()) {
            return true;
        }
        return section.parseWarning() != null && !section.parseWarning().isBlank();
    }

    private String sectionLabel(TraceabilityModels.ParsedSectionRow section) {
        if (section == null) {
            return "-";
        }
        if (section.sectionKey() == null || section.sectionKey().isBlank()) {
            return "unknown section";
        }
        String reportLabel = resolveReportSectionRule(section)
                .map(ReportSectionRule::displayLabel)
                .orElse(null);
        if (reportLabel != null) {
            return reportLabel;
        }
        return Stream.of(section.sectionKey().trim().split("[-_\\s]+"))
                .filter(token -> !token.isBlank())
                .map(token -> token.substring(0, 1).toUpperCase(Locale.ROOT) + token.substring(1))
                .collect(Collectors.joining(" "));
    }

    private Optional<ReportSectionRule> resolveReportSectionRule(TraceabilityModels.ParsedSectionRow section) {
        if (!isReportSection(section) || section.sectionKey() == null || section.sectionKey().isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(REPORT_SECTION_RULES.get(normalizeSectionLookupKey(section.sectionKey())));
    }

    private boolean isReportSection(TraceabilityModels.ParsedSectionRow section) {
        return REPORT_ARTIFACT_TYPE.equals(normalizeArtifactTypeCode(section));
    }

    private String normalizeArtifactTypeCode(TraceabilityModels.ParsedSectionRow section) {
        if (section == null || section.artifactTypeCode() == null) {
            return "";
        }
        return section.artifactTypeCode().trim().toUpperCase(Locale.ROOT);
    }

    private static Map<String, ReportSectionRule> createReportSectionRules() {
        Map<String, ReportSectionRule> rules = new LinkedHashMap<>();
        addReportSectionRule(rules, "Tóm tắt thay đổi", false,
                "tom_tat_thay_doi", "ly_do", "edited_summary", "summary");
        addReportSectionRule(rules, "Tóm tắt thay đổi", true, "da_thay_doi_gi");

        addReportSectionRule(rules, "Phạm vi ảnh hưởng", false,
                "pham_vi_anh_huong", "scope_of_influence", "impact_scope");
        addReportSectionRule(rules, "Kết quả review", false,
                "ket_qua_review", "review_results");
        addReportSectionRule(rules, "Kết quả kiểm thử", false,
                "ket_qua_kiem_thu", "test_results");
        addReportSectionRule(rules, "Công việc còn lại / Hành động tiếp theo", false,
                "cong_viec_con_lai_hanh_dong_tiep_theo",
                "remaining_work_next_actions",
                "remaining_work_next_action",
                "next_actions",
                "open_issues");
        addReportSectionRule(rules, "Quy trình hoàn tác", false,
                "quy_trinh_hoan_tac", "rollback_procedure", "rollback_plan", "rollback");
        addReportSectionRule(rules, "Danh mục đầu ra", false,
                "danh_muc_dau_ra", "output_inventory", "deliverables", "output_catalog", "output_artifacts");
        return Map.copyOf(rules);
    }

    private static void addReportSectionRule(
            Map<String, ReportSectionRule> rules,
            String displayLabel,
            boolean ignoreAsBrokenLink,
            String... keys
    ) {
        ReportSectionRule rule = new ReportSectionRule(displayLabel, ignoreAsBrokenLink);
        for (String key : keys) {
            rules.put(key, rule);
        }
    }

    private static String normalizeSectionLookupKey(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private static TraceabilityModels.ArtifactCoverageRow preferLatest(
            TraceabilityModels.ArtifactCoverageRow left,
            TraceabilityModels.ArtifactCoverageRow right
    ) {
        if (left.collectedAt() == null) {
            return right;
        }
        if (right.collectedAt() == null) {
            return left;
        }
        return right.collectedAt().isAfter(left.collectedAt()) ? right : left;
    }

    private static String defaultArtifactName(String artifactTypeCode) {
        return switch (artifactTypeCode) {
            case "SPEC_PACK" -> "Spec Pack";
            case "IMPL_PLAN" -> "Implementation Plan";
            case "REVIEW_CHECKLIST" -> "Review Checklist";
            case "SELF_REVIEW" -> "Self Review";
            case "TEST_PLAN" -> "Test Plan";
            case "TEST_RESULTS" -> "Test Results";
            case "REPORT" -> "Report";
            default -> artifactTypeCode;
        };
    }

    private static String defaultFileName(String artifactTypeCode) {
        return switch (artifactTypeCode) {
            case "SPEC_PACK" -> "spec-pack.md";
            case "IMPL_PLAN" -> "impl-plan.md";
            case "REVIEW_CHECKLIST" -> "review-checklist.md";
            case "SELF_REVIEW" -> "self-review.md";
            case "TEST_PLAN" -> "test-plan.md";
            case "TEST_RESULTS" -> "test-results.md";
            case "REPORT" -> "report.md";
            default -> artifactTypeCode.toLowerCase(Locale.ROOT);
        };
    }

    private static String safeSortValue(UUID value) {
        return value == null ? "" : value.toString();
    }

    private record ReportSectionRule(String displayLabel, boolean ignoreAsBrokenLink) {
    }
}
