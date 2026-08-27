package com.sdd.platform.application.usecase.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.integration.ArtifactScannerSourcePort;
import com.sdd.platform.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class GithubSecurityEvidenceSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(GithubSecurityEvidenceSnapshotService.class);
    private static final String WORKFLOW_JOB_NAME = "pull_request_scan";
    private static final String WORKFLOW_RUN_ID_PREFIX = "pr-scan:";

    private final AppProperties props;
    private final ArtifactScannerSourcePort artifactScannerSourcePort;
    private final SecurityEvidenceIngestService ingestService;
    private final ObjectMapper objectMapper;

    public GithubSecurityEvidenceSnapshotService(AppProperties props,
                                                 ArtifactScannerSourcePort artifactScannerSourcePort,
                                                 SecurityEvidenceIngestService ingestService,
                                                 ObjectMapper objectMapper) {
        this.props = props;
        this.artifactScannerSourcePort = artifactScannerSourcePort;
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
    }

    public Result collectFromPullRequest(String repositoryFullName,
                                         String branchName,
                                         String revision,
                                         Integer pullRequestNumber,
                                         List<String> changedFilePaths,
                                         String deliveryId) {
        return collect(repositoryFullName, branchName, revision, pullRequestNumber, changedFilePaths,
                WORKFLOW_RUN_ID_PREFIX + (deliveryId == null || deliveryId.isBlank() ? revision : deliveryId.trim()),
                null,
                WORKFLOW_JOB_NAME,
                deliveryId,
                false);
    }

    public Result collectFromWorkflowJob(String repositoryFullName,
                                         String branchName,
                                         String revision,
                                         Integer pullRequestNumber,
                                         String workflowRunId,
                                         String workflowJobId,
                                         String workflowJobName,
                                         String deliveryId) {
        return collect(repositoryFullName, branchName, revision, pullRequestNumber, List.of(),
                workflowRunId, workflowJobId, workflowJobName, deliveryId, true);
    }

    private Result collect(String repositoryFullName,
                           String branchName,
                           String revision,
                           Integer pullRequestNumber,
                           List<String> changedFilePaths,
                           String workflowRunId,
                           String workflowJobId,
                           String workflowJobName,
                           String deliveryId,
                           boolean includeSafetyPackWhenMissingSourceDir) {
        try {
            if (repositoryFullName == null || repositoryFullName.isBlank()) {
                return new Result("missing-repository", 0);
            }
            if (revision == null || revision.isBlank()) {
                return new Result("missing-revision", 0);
            }

            ArtifactScannerSourcePort.ResolvedRevision resolvedRevision =
                    artifactScannerSourcePort.resolveRevision(repositoryFullName, revision);
            Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree =
                    artifactScannerSourcePort.listTree(repositoryFullName, resolvedRevision.revisionSha());

            List<String> safeChangedPaths = changedFilePaths == null ? List.of() : changedFilePaths.stream()
                    .filter(path -> path != null && !path.isBlank())
                    .distinct()
                    .toList();

            Map<String, Object> payload = buildPayload(
                    repositoryFullName,
                    branchName,
                    resolvedRevision.revisionSha(),
                    pullRequestNumber,
                    safeChangedPaths,
                    tree,
                    workflowRunId,
                    workflowJobId,
                    workflowJobName,
                    deliveryId,
                    includeSafetyPackWhenMissingSourceDir
            );
            if (payload == null) {
                return new Result("ignored:no-security-evidence", 0);
            }

            byte[] rawBody = objectMapper.writeValueAsBytes(payload);
            String signature = "sha256=" + hmacSha256Hex(secret(), rawBody);
            SecurityEvidenceIngestService.Result result = ingestService.handle(rawBody, signature);
            return new Result(result.handled(), result.recordsAffected());
        } catch (Exception ex) {
            log.warn("PR security evidence snapshot failed for repo={} delivery={}: {}",
                    repositoryFullName, deliveryId, ex.getMessage(), ex);
            return new Result("security_evidence_failed", 0);
        }
    }

    private Map<String, Object> buildPayload(String repositoryFullName,
                                             String branchName,
                                             String commitSha,
                                             Integer pullRequestNumber,
                                             List<String> changedFilePaths,
                                             Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree,
                                             String workflowRunId,
                                             String workflowJobId,
                                             String workflowJobName,
                                             String deliveryId,
                                             boolean includeSafetyPackWhenMissingSourceDir) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("repositoryNameMasked", repositoryFullName);
        payload.put("branchName", branchName == null ? "" : branchName.trim());
        payload.put("commitSha", commitSha);
        if (pullRequestNumber != null && pullRequestNumber > 0) {
            payload.put("pullRequestNumber", pullRequestNumber);
        }
        payload.put("workflowRunId", workflowRunId == null || workflowRunId.isBlank()
                ? WORKFLOW_RUN_ID_PREFIX + (deliveryId == null || deliveryId.isBlank() ? commitSha : deliveryId.trim())
                : workflowRunId.trim());
        if (workflowJobId != null && !workflowJobId.isBlank()) {
            payload.put("workflowJobId", workflowJobId.trim());
        }
        payload.put("workflowJobName", workflowJobName == null || workflowJobName.isBlank()
                ? WORKFLOW_JOB_NAME
                : workflowJobName.trim());
        payload.put("scannedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());

        List<Map<String, Object>> scans = new ArrayList<>();
        scans.add(buildSecurityScan(repositoryFullName, "SECRET", "Gitleaks", "gitleaks", changedFilePaths, tree));
        scans.add(buildSecurityScan(repositoryFullName, "SAST", "Semgrep", "semgrep", changedFilePaths, tree));
        scans.add(buildSecurityScan(repositoryFullName, "SCA", "Trivy", "trivy", changedFilePaths, tree));
        payload.put("scans", scans);

        Map<String, Object> safetyPack = buildSafetyPack(repositoryFullName, branchName, commitSha, tree, includeSafetyPackWhenMissingSourceDir);
        if (safetyPack != null) {
            payload.put("safetyPack", safetyPack);
        }

        boolean hasAnyScan = !scans.isEmpty();
        boolean hasSafetyPackSignal = safetyPack != null;
        if (!hasAnyScan && !hasSafetyPackSignal) {
            return null;
        }
        return payload;
    }

    private Map<String, Object> buildSecurityScan(String repositoryFullName,
                                                  String scanType,
                                                  String scannerName,
                                                  String scanTool,
                                                  List<String> changedFilePaths,
                                                  Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree) {
        List<String> sourcePaths = changedFilePaths == null || changedFilePaths.isEmpty()
                ? new ArrayList<>(tree.keySet())
                : changedFilePaths;

        List<String> evidencePaths = sourcePaths.stream()
                .filter(path -> matchesScanType(path, scanType))
                .sorted(Comparator.naturalOrder())
                .toList();

        ScanCounts counts = new ScanCounts();
        for (String path : evidencePaths) {
            ArtifactScannerSourcePort.GitHubTreeEntry entry = tree.get(path);
            if (entry == null || entry.sha() == null || entry.sha().isBlank()) {
                continue;
            }
            String content = readBlobAsText(repositoryFullName, entry.sha());
            counts.merge(parseCounts(scanType, path, content));
        }

        if (counts.isEmpty() && !evidencePaths.isEmpty()) {
            counts = pathBasedFallback(scanType, evidencePaths);
        }

        String scanStatus = resolveScanStatus(scanType, counts, evidencePaths.isEmpty());
        String dbStatus = switch (scanStatus) {
            case "FAIL" -> "FAILED";
            case "WARNING" -> "SUCCESS";
            case "PASS" -> "SUCCESS";
            default -> "UNKNOWN";
        };

        Map<String, Object> scan = new LinkedHashMap<>();
        scan.put("scanType", scanType);
        scan.put("scannerName", scannerName);
        scan.put("scanTool", scanTool);
        scan.put("scanStatus", scanStatus);
        scan.put("status", dbStatus);
        scan.put("findingCount", counts.findingCount());
        scan.put("unresolvedCount", counts.unresolvedCount());
        scan.put("criticalCount", counts.criticalCount());
        scan.put("highCount", counts.highCount());
        scan.put("mediumCount", counts.mediumCount());
        scan.put("lowCount", counts.lowCount());
        scan.put("infoCount", counts.infoCount());
        scan.put("startedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        scan.put("finishedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        return scan;
    }

    private Map<String, Object> buildSafetyPack(String repositoryFullName,
                                                String branchName,
                                                String commitSha,
                                                Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree,
                                                boolean includeEvenIfSourceDirMissing) {
        String sourceDir = resolveSourceDir(tree);
        if (sourceDir == null && !includeEvenIfSourceDirMissing) {
            return null;
        }

        boolean claudeMdExists = sourceDir != null && tree.containsKey(sourceDir + "CLAUDE.md");
        boolean settingsJsonExists = sourceDir != null && tree.containsKey(sourceDir + "settings.json");
        boolean rulesExists = sourceDir != null && tree.keySet().stream().anyMatch(path -> path.startsWith(sourceDir + "rules/"));
        long rulesCount = tree.keySet().stream()
                .filter(path -> sourceDir != null && path.startsWith(sourceDir + "rules/"))
                .filter(path -> path.toLowerCase(Locale.ROOT).endsWith(".md"))
                .count();

        SettingsSummary summary = parseSettings(repositoryFullName, tree, sourceDir);
        String missingItemsSummary = buildMissingItemsSummary(claudeMdExists, settingsJsonExists, rulesExists, (int) rulesCount);
        String scanStatus = sourceDir == null
                ? "MISSING"
                : computeSafetyPackStatus(summary.parseStatus(), claudeMdExists, settingsJsonExists, rulesExists, (int) rulesCount);
        String contentHash = computeContentHash(repositoryFullName, tree, sourceDir);

        Map<String, Object> safetyPack = new LinkedHashMap<>();
        safetyPack.put("repositoryNameMasked", repositoryFullName);
        safetyPack.put("branchName", branchName == null ? null : branchName.trim());
        safetyPack.put("commitSha", commitSha);
        safetyPack.put("claudeMdExists", claudeMdExists);
        safetyPack.put("settingsJsonExists", settingsJsonExists);
        safetyPack.put("rulesExists", rulesExists);
        safetyPack.put("denyRuleCount", summary.denyCount());
        safetyPack.put("askRuleCount", summary.askCount());
        safetyPack.put("allowRuleCount", summary.allowCount());
        safetyPack.put("reviewedFlag", Boolean.FALSE);
        safetyPack.put("scanStatus", scanStatus);
        safetyPack.put("settingsParseStatus", summary.parseStatus());
        safetyPack.put("contentHash", contentHash);
        safetyPack.put("missingItemsSummary", missingItemsSummary);
        safetyPack.put("createdBy", "SYSTEM");
        safetyPack.put("updatedBy", "SYSTEM");
        safetyPack.put("collectedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        safetyPack.put("lastUpdatedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        return safetyPack;
    }

    private String resolveSourceDir(Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree) {
        if (tree == null || tree.isEmpty()) {
            return null;
        }

        List<String> candidates = tree.keySet().stream()
                .filter(path -> path != null && path.toLowerCase(Locale.ROOT).contains("/.claude/"))
                .sorted()
                .toList();
        if (!candidates.isEmpty()) {
            return sourceDirPrefix(candidates.get(0));
        }

        List<String> rootCandidates = tree.keySet().stream()
                .filter(path -> path != null && path.toLowerCase(Locale.ROOT).startsWith(".claude/"))
                .sorted()
                .toList();
        if (!rootCandidates.isEmpty()) {
            return sourceDirPrefix(rootCandidates.get(0));
        }

        return null;
    }

    private String sourceDirPrefix(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        int idx = path.toLowerCase(Locale.ROOT).indexOf(".claude/");
        if (idx < 0) {
            return null;
        }
        return path.substring(0, idx + ".claude/".length());
    }

    private SettingsSummary parseSettings(String repositoryFullName,
                                          Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree,
                                          String sourceDir) {
        ArtifactScannerSourcePort.GitHubTreeEntry entry = tree.get(sourceDir + "settings.json");
        if (entry == null || entry.sha() == null || entry.sha().isBlank()) {
            return SettingsSummary.missing();
        }
        try {
            JsonNode root = objectMapper.readTree(readBlobAsText(repositoryFullName, entry.sha()));
            return new SettingsSummary(
                    countPermissionEntries(root, "deny"),
                    countPermissionEntries(root, "ask"),
                    countPermissionEntries(root, "allow"),
                    "OK"
            );
        } catch (Exception ex) {
            return SettingsSummary.error();
        }
    }

    private String buildMissingItemsSummary(boolean claudeMdExists,
                                            boolean settingsJsonExists,
                                            boolean rulesExists,
                                            int rulesCount) {
        List<String> missing = new ArrayList<>();
        if (!claudeMdExists) {
            missing.add("CLAUDE.md");
        }
        if (!settingsJsonExists) {
            missing.add("settings.json");
        }
        if (!rulesExists) {
            missing.add("rules/");
        } else if (rulesCount == 0) {
            missing.add("rules/*.md");
        }
        return missing.isEmpty() ? null : String.join(", ", missing);
    }

    private String computeSafetyPackStatus(String parseStatus,
                                          boolean claudeMdExists,
                                          boolean settingsJsonExists,
                                          boolean rulesExists,
                                          int rulesCount) {
        if ("ERROR".equals(parseStatus)) {
            return "PARSE_ERROR";
        }
        if (claudeMdExists && settingsJsonExists && rulesExists && rulesCount > 0) {
            return "READY";
        }
        return "WARNING";
    }

    private String computeContentHash(String repositoryFullName,
                                      Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree,
                                      String sourceDir) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, readBlobAsBytes(repositoryFullName, tree.get(sourceDir + "CLAUDE.md")));
            updateDigest(digest, readBlobAsBytes(repositoryFullName, tree.get(sourceDir + "settings.json")));
            tree.keySet().stream()
                    .filter(path -> path.startsWith(sourceDir + "rules/"))
                    .sorted()
                    .forEach(path -> updateDigest(digest, readBlobAsBytes(repositoryFullName, tree.get(path))));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            return null;
        }
    }

    private void updateDigest(MessageDigest digest, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        digest.update(bytes);
        digest.update((byte) 0);
    }

    private ScanCounts parseCounts(String scanType, String path, String content) {
        if (content == null || content.isBlank()) {
            return new ScanCounts();
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            ScanCounts counts = extractCountsFromJson(root);
            if (!counts.isEmpty()) {
                return counts;
            }
        } catch (Exception ignored) {
            // Fall back to text parsing below.
        }
        ScanCounts counts = extractCountsFromText(content);
        if (!counts.isEmpty()) {
            return counts;
        }
        return counts;
    }

    private ScanCounts extractCountsFromJson(JsonNode node) {
        ScanCounts counts = new ScanCounts();
        counts.setFindingCount(findInt(node, "findingCount"));
        counts.setUnresolvedCount(findInt(node, "unresolvedCount"));
        counts.setCriticalCount(findInt(node, "criticalCount"));
        counts.setHighCount(findInt(node, "highCount"));
        counts.setMediumCount(findInt(node, "mediumCount"));
        counts.setLowCount(findInt(node, "lowCount"));
        counts.setInfoCount(findInt(node, "infoCount"));
        if (counts.findingCount() <= 0) {
            counts.setFindingCount(counts.unresolvedCount() + counts.criticalCount() + counts.highCount()
                    + counts.mediumCount() + counts.lowCount() + counts.infoCount());
        }
        return counts;
    }

    private ScanCounts extractCountsFromText(String content) {
        ScanCounts counts = new ScanCounts();
        counts.setFindingCount(findCount(content, "finding"));
        counts.setUnresolvedCount(findCount(content, "unresolved"));
        counts.setCriticalCount(findCount(content, "critical"));
        counts.setHighCount(findCount(content, "high"));
        counts.setMediumCount(findCount(content, "medium"));
        counts.setLowCount(findCount(content, "low"));
        counts.setInfoCount(findCount(content, "info"));
        if (counts.findingCount() <= 0) {
            counts.setFindingCount(counts.unresolvedCount() + counts.criticalCount() + counts.highCount()
                    + counts.mediumCount() + counts.lowCount() + counts.infoCount());
        }
        return counts;
    }

    private ScanCounts pathBasedFallback(String scanType, List<String> evidencePaths) {
        boolean hasFailHint = evidencePaths.stream().anyMatch(path -> containsAny(normalizePath(path), "fail", "finding", "alert", "critical", "issue"));
        boolean hasWarnHint = evidencePaths.stream().anyMatch(path -> containsAny(normalizePath(path), "warn", "warning", "high"));

        if ("SECRET".equals(scanType)) {
            return hasFailHint ? new ScanCounts(1, 1, 0, 0, 0, 0, 0) : new ScanCounts();
        }
        if ("SAST".equals(scanType) || "SCA".equals(scanType)) {
            if (hasFailHint) {
                return new ScanCounts(1, 0, 1, 0, 0, 0, 0);
            }
            if (hasWarnHint) {
                return new ScanCounts(1, 0, 0, 1, 0, 0, 0);
            }
            return new ScanCounts();
        }
        return new ScanCounts();
    }

    private String resolveScanStatus(String scanType, ScanCounts counts, boolean noEvidencePaths) {
        if (noEvidencePaths) {
            return "NOT_AVAILABLE";
        }
        if ("SECRET".equals(scanType)) {
            return counts.unresolvedCount() > 0 ? "FAIL" : "PASS";
        }
        if ("SAST".equals(scanType) || "SCA".equals(scanType)) {
            if (counts.criticalCount() > 0) {
                return "FAIL";
            }
            if (counts.highCount() > 0) {
                return "WARNING";
            }
            return "PASS";
        }
        return "UNKNOWN";
    }

    private boolean matchesScanType(String path, String scanType) {
        String normalized = normalizePath(path);
        return switch (scanType) {
            case "SECRET" -> containsAny(normalized, "secret", "secrets", "gitleaks", "leaks", "credential", "credentials", "token", "secret-scan");
            case "SAST" -> containsAny(normalized, "sast", "semgrep", "codeql", "static-analysis", "source-scan");
            case "SCA" -> containsAny(normalized, "sca", "trivy", "dependency", "dependencies", "sbom", "package-audit", "license-scan");
            default -> false;
        };
    }

    private boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String normalizePath(String path) {
        return path == null ? "" : path.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
    }

    private int findInt(JsonNode node, String key) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0;
        }
        if (node.isObject()) {
            JsonNode direct = node.get(key);
            if (direct != null && direct.isNumber()) {
                return Math.max(direct.asInt(), 0);
            }
            if (direct != null && direct.isTextual()) {
                try {
                    return Math.max(Integer.parseInt(direct.asText().trim()), 0);
                } catch (NumberFormatException ignored) {
                    // continue
                }
            }
            for (JsonNode child : node) {
                int found = findInt(child, key);
                if (found > 0) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                int found = findInt(child, key);
                if (found > 0) {
                    return found;
                }
            }
        }
        return 0;
    }

    private int findCount(String content, String key) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        String token = key.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(token);
        if (idx < 0) {
            return 0;
        }
        String tail = lower.substring(idx);
        String digits = tail.replaceFirst("^[^0-9]*", "");
        int end = 0;
        while (end < digits.length() && Character.isDigit(digits.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(digits.substring(0, end)), 0);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private byte[] readBlobAsBytes(String repositoryFullName, ArtifactScannerSourcePort.GitHubTreeEntry entry) {
        if (entry == null || entry.sha() == null || entry.sha().isBlank()) {
            return null;
        }
        try {
            return artifactScannerSourcePort.readBlob(repositoryFullName, entry.sha());
        } catch (WebClientResponseException ex) {
            if (isSkippableBlobError(ex)) {
                log.warn("Skipping missing GitHub blob repo={} path={} sha={}: {}",
                        repositoryFullName, entry.path(), entry.sha(), ex.getMessage());
                return null;
            }
            throw ex;
        }
    }

    private String readBlobAsText(String repositoryFullName, String blobSha) {
        if (blobSha == null || blobSha.isBlank()) {
            return "";
        }
        byte[] bytes;
        try {
            bytes = artifactScannerSourcePort.readBlob(repositoryFullName, blobSha);
        } catch (WebClientResponseException ex) {
            if (isSkippableBlobError(ex)) {
                log.warn("Skipping missing GitHub blob repo={} sha={}: {}", repositoryFullName, blobSha, ex.getMessage());
                return "";
            }
            throw ex;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private boolean isSkippableBlobError(WebClientResponseException ex) {
        int status = ex.getStatusCode().value();
        return status == 401 || status == 403 || status == 404;
    }

    private int countPermissionEntries(JsonNode root, String key) {
        JsonNode node = findNode(root, key);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0;
        }
        if (node.isArray()) {
            return node.size();
        }
        if (node.isNumber()) {
            return Math.max(node.asInt(), 0);
        }
        if (node.isTextual()) {
            try {
                return Math.max(Integer.parseInt(node.asText().trim()), 0);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private JsonNode findNode(JsonNode node, String key) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            JsonNode direct = node.get(key);
            if (direct != null) {
                return direct;
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                JsonNode child = fields.next().getValue();
                JsonNode found = findNode(child, key);
                if (found != null && !found.isMissingNode()) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findNode(child, key);
                if (found != null && !found.isMissingNode()) {
                    return found;
                }
            }
        }
        return null;
    }

    private byte[] secret() {
        String secret = props.connectors().github().webhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new SecurityException("EDCAP_WEBHOOK_SECRET not configured");
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    private String hmacSha256Hex(byte[] secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC computation failed", ex);
        }
    }

    public record Result(String handled, int recordsAffected) {
    }

    private record SettingsSummary(int denyCount, int askCount, int allowCount, String parseStatus) {
        static SettingsSummary missing() {
            return new SettingsSummary(0, 0, 0, "MISSING");
        }

        static SettingsSummary error() {
            return new SettingsSummary(0, 0, 0, "ERROR");
        }
    }

    private static final class ScanCounts {
        private int findingCount;
        private int unresolvedCount;
        private int criticalCount;
        private int highCount;
        private int mediumCount;
        private int lowCount;
        private int infoCount;

        private ScanCounts() {
        }

        private ScanCounts(int findingCount,
                           int unresolvedCount,
                           int criticalCount,
                           int highCount,
                           int mediumCount,
                           int lowCount,
                           int infoCount) {
            this.findingCount = findingCount;
            this.unresolvedCount = unresolvedCount;
            this.criticalCount = criticalCount;
            this.highCount = highCount;
            this.mediumCount = mediumCount;
            this.lowCount = lowCount;
            this.infoCount = infoCount;
        }

        private void merge(ScanCounts other) {
            if (other == null) {
                return;
            }
            this.findingCount += other.findingCount;
            this.unresolvedCount += other.unresolvedCount;
            this.criticalCount += other.criticalCount;
            this.highCount += other.highCount;
            this.mediumCount += other.mediumCount;
            this.lowCount += other.lowCount;
            this.infoCount += other.infoCount;
        }

        private boolean isEmpty() {
            return findingCount == 0
                    && unresolvedCount == 0
                    && criticalCount == 0
                    && highCount == 0
                    && mediumCount == 0
                    && lowCount == 0
                    && infoCount == 0;
        }

        private int findingCount() {
            return findingCount;
        }

        private void setFindingCount(int value) {
            this.findingCount = Math.max(value, 0);
        }

        private int unresolvedCount() {
            return unresolvedCount;
        }

        private void setUnresolvedCount(int value) {
            this.unresolvedCount = Math.max(value, 0);
        }

        private int criticalCount() {
            return criticalCount;
        }

        private void setCriticalCount(int value) {
            this.criticalCount = Math.max(value, 0);
        }

        private int highCount() {
            return highCount;
        }

        private void setHighCount(int value) {
            this.highCount = Math.max(value, 0);
        }

        private int mediumCount() {
            return mediumCount;
        }

        private void setMediumCount(int value) {
            this.mediumCount = Math.max(value, 0);
        }

        private int lowCount() {
            return lowCount;
        }

        private void setLowCount(int value) {
            this.lowCount = Math.max(value, 0);
        }

        private int infoCount() {
            return infoCount;
        }

        private void setInfoCount(int value) {
            this.infoCount = Math.max(value, 0);
        }
    }
}
