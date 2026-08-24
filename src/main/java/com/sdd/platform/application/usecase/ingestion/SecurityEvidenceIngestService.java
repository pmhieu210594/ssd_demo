package com.sdd.platform.application.usecase.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.persistence.EvidenceRepositoryPort;
import com.sdd.platform.application.port.out.persistence.CiRunRepositoryPort;
import com.sdd.platform.application.port.out.persistence.SafetyPackStatusRepositoryPort;
import com.sdd.platform.application.port.out.persistence.SecurityScanRepositoryPort;
import com.sdd.platform.config.AppProperties;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.EvidenceRepository;
import com.sdd.platform.domain.model.SafetyPackStatus;
import com.sdd.platform.domain.model.SecurityScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class SecurityEvidenceIngestService {

    private static final Logger log = LoggerFactory.getLogger(SecurityEvidenceIngestService.class);

    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private final EvidenceRepositoryPort repositoryPort;
    private final CiRunRepositoryPort ciRunRepositoryPort;
    private final SafetyPackStatusRepositoryPort safetyPackStatusRepositoryPort;
    private final SecurityScanRepositoryPort scanRepositoryPort;

    public SecurityEvidenceIngestService(AppProperties props,
            ObjectMapper objectMapper,
            EvidenceRepositoryPort repositoryPort,
            CiRunRepositoryPort ciRunRepositoryPort,
            SafetyPackStatusRepositoryPort safetyPackStatusRepositoryPort,
            SecurityScanRepositoryPort scanRepositoryPort) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.repositoryPort = repositoryPort;
        this.ciRunRepositoryPort = ciRunRepositoryPort;
        this.safetyPackStatusRepositoryPort = safetyPackStatusRepositoryPort;
        this.scanRepositoryPort = scanRepositoryPort;
    }

    @Transactional
    public Result handle(byte[] rawBody, String signatureHeader) {
        verifySignature(rawBody, signatureHeader);

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Malformed JSON payload");
        }

        String repositoryNameMasked = text(payload, "repositoryNameMasked", "repository");
        EvidenceRepository repository = repositoryPort
                .findByRepositoryNameMaskedAndHostType(repositoryNameMasked, "GITHUB")
                .orElseThrow(() -> new NotFoundException("Pages.SafetyPack.Repository.NotFound"));

        String branchName = textOrNull(payload, "branchName", "branch");
        String commitSha = text(payload, "commitSha", "commit");
        Integer pullRequestNumber = optionalInteger(payload, "pullRequestNumber", "prNumber");
        String workflowRunId = text(payload, "workflowRunId", "workflowRun");
        String workflowJobId = textOrNull(payload, "workflowJobId", "externalJobId");
        String workflowJobName = text(payload, "workflowJobName", "jobName");
        OffsetDateTime scannedAt = optionalTimestamp(payload, "scannedAt", "collectedAt");
        Linkage linkage = resolveLinkage(repository, branchName, commitSha, pullRequestNumber, workflowRunId,
                workflowJobId, workflowJobName);

        JsonNode scans = payload.path("scans");
        if (!scans.isArray() || scans.isEmpty()) {
            throw new IllegalArgumentException("Missing scans array");
        }

        int ingested = 0;
        for (JsonNode scanNode : scans) {
            persistOne(
                    repository,
                    repositoryNameMasked,
                    branchName,
                    commitSha,
                    pullRequestNumber,
                    linkage.ticketId(),
                    linkage.prId(),
                    linkage.ciRunId(),
                    workflowRunId,
                    workflowJobId,
                    workflowJobName,
                    scannedAt,
                    scanNode);
            ingested++;
        }

        JsonNode safetyPackNode = payload.path("safetyPack");
        if (safetyPackNode.isObject()) {
            persistSafetyPack(repository, safetyPackNode);
            ingested++;
        }

        log.info("Security evidence ingested: repository={} records={}", repositoryNameMasked, ingested);
        return new Result("security_evidence", ingested);
    }

    private void persistOne(EvidenceRepository repository,
            String repositoryNameMasked,
            String branchName,
            String commitSha,
            Integer pullRequestNumber,
            UUID ticketId,
            UUID prId,
            UUID ciRunId,
            String workflowRunId,
            String workflowJobId,
            String workflowJobName,
            OffsetDateTime scannedAt,
            JsonNode scanNode) {
        String scanType = text(scanNode, "scanType");
        String scannerName = text(scanNode, "scannerName", "tool");
        String scanTool = text(scanNode, "scanTool", "scannerName", "tool");
        ScanPolicy policy = ScanPolicy.from(scanType);

        int criticalCount = intValue(scanNode, "criticalCount");
        int highCount = intValue(scanNode, "highCount");
        int mediumCount = intValue(scanNode, "mediumCount");
        int lowCount = intValue(scanNode, "lowCount");
        int infoCount = intValue(scanNode, "infoCount");
        int unresolvedCount = intValue(scanNode, "unresolvedCount");
        int findingCount = intValue(scanNode, "findingCount");
        if (findingCount < 0) {
            findingCount = criticalCount + highCount + mediumCount + lowCount + infoCount;
        }

        String severity = resolveSeverity(criticalCount, highCount, mediumCount, lowCount, infoCount);
        String normalizedStatus = policy.resolveScanStatus(unresolvedCount, criticalCount, highCount);
        String dbStatus = "FAIL".equals(normalizedStatus) ? "FAILED" : "SUCCESS";
        if ("ERROR".equals(normalizedStatus) || "NOT_AVAILABLE".equals(normalizedStatus)) {
            dbStatus = "UNKNOWN";
        }

        SecurityScan row = SecurityScan.builder()
                .securityScanId(UUID.randomUUID())
                .repositoryId(repository.getRepositoryId())
                .repositoryNameMasked(repositoryNameMasked)
                .ticketId(ticketId)
                .prId(prId)
                .ciRunId(ciRunId)
                .branchName(branchName)
                .commitSha(commitSha)
                .pullRequestNumber(pullRequestNumber)
                .workflowRunId(workflowRunId)
                .workflowJobName(workflowJobName)
                .scannerType(policy.name())
                .scannerName(scannerName)
                .scanTool(scanTool)
                .status(dbStatus)
                .scanStatus(normalizedStatus)
                .severity(severity)
                .findingCount(findingCount)
                .unresolvedCount(unresolvedCount)
                .criticalCount(criticalCount)
                .highCount(highCount)
                .mediumCount(mediumCount)
                .lowCount(lowCount)
                .infoCount(infoCount)
                .summary(buildSummary(policy.name(), scanTool, normalizedStatus, findingCount, unresolvedCount,
                        criticalCount, highCount, mediumCount, lowCount, infoCount))
                .scanCountsJson(buildScanCountsJson(
                        policy.name(),
                        scannerName,
                        scanTool,
                        normalizedStatus,
                        severity,
                        findingCount,
                        unresolvedCount,
                        criticalCount,
                        highCount,
                        mediumCount,
                        lowCount,
                        infoCount,
                        branchName,
                        commitSha,
                        workflowRunId,
                        workflowJobName,
                        pullRequestNumber))
                .startedAt(optionalTimestamp(scanNode, "startedAt", "started_at"))
                .finishedAt(optionalTimestamp(scanNode, "finishedAt", "finished_at"))
                .collectedAt(scannedAt != null ? scannedAt : OffsetDateTime.now())
                .build();
        scanRepositoryPort.save(row);
    }

    private void persistSafetyPack(EvidenceRepository repository, JsonNode safetyPackNode) {
        OffsetDateTime now = OffsetDateTime.now();
        Boolean claudeMdExists = booleanValue(safetyPackNode, "claudeMdExists");
        Boolean settingsJsonExists = booleanValue(safetyPackNode, "settingsJsonExists");
        Boolean rulesExists = booleanValue(safetyPackNode, "rulesExists");
        Boolean reviewedFlag = booleanValue(safetyPackNode, "reviewedFlag");
        OffsetDateTime lastUpdatedAt = optionalTimestamp(safetyPackNode, "lastUpdatedAt", "updatedAt");
        OffsetDateTime collectedAt = optionalTimestamp(safetyPackNode, "collectedAt");
        OffsetDateTime createdAt = optionalTimestamp(safetyPackNode, "createdAt");
        OffsetDateTime updatedAt = optionalTimestamp(safetyPackNode, "updatedAt");
        String branchName = textOrNull(safetyPackNode, "branchName", "branch");
        String commitSha = textOrNull(safetyPackNode, "commitSha", "commit");
        String scanStatus = textOrNull(safetyPackNode, "scanStatus");
        String settingsParseStatus = textOrNull(safetyPackNode, "settingsParseStatus");
        String contentHash = textOrNull(safetyPackNode, "contentHash");
        String missingItemsSummary = textOrNull(safetyPackNode, "missingItemsSummary");
        String createdBy = textOrNull(safetyPackNode, "createdBy");
        String updatedBy = textOrNull(safetyPackNode, "updatedBy");
        SafetyPackStatus row = SafetyPackStatus.builder()
                .safetyPackStatusId(UUID.randomUUID())
                .repositoryId(repository.getRepositoryId())
                .repositoryNameMasked(repository.getRepositoryNameMasked())
                .claudeMdExists(claudeMdExists != null ? claudeMdExists : Boolean.FALSE)
                .settingsJsonExists(settingsJsonExists != null ? settingsJsonExists : Boolean.FALSE)
                .rulesExists(rulesExists != null ? rulesExists : Boolean.FALSE)
                .denyRuleCount(intValue(safetyPackNode, "denyRuleCount"))
                .askRuleCount(intValue(safetyPackNode, "askRuleCount"))
                .allowRuleCount(intValue(safetyPackNode, "allowRuleCount"))
                .reviewedFlag(reviewedFlag != null ? reviewedFlag : Boolean.FALSE)
                .reviewedByRoleId(optionalUuid(safetyPackNode, "reviewedByRoleId"))
                .lastUpdatedAt(lastUpdatedAt != null ? lastUpdatedAt : now)
                .collectedAt(collectedAt != null ? collectedAt : now)
                .branchName(branchName)
                .commitSha(commitSha)
                .scanStatus(scanStatus != null ? scanStatus : "MISSING")
                .settingsParseStatus(settingsParseStatus != null ? settingsParseStatus : "MISSING")
                .contentHash(contentHash)
                .missingItemsSummary(missingItemsSummary)
                .createdAt(createdAt != null ? createdAt : now)
                .createdBy(createdBy != null ? createdBy : "SYSTEM")
                .updatedAt(updatedAt != null ? updatedAt : now)
                .updatedBy(updatedBy != null ? updatedBy : "SYSTEM")
                .build();
        safetyPackStatusRepositoryPort.save(row);
    }

    private String buildSummary(String scanType,
            String scanTool,
            String status,
            int findingCount,
            int unresolvedCount,
            int criticalCount,
            int highCount,
            int mediumCount,
            int lowCount,
            int infoCount) {
        return String.format(
                "%s/%s status=%s findings=%d unresolved=%d critical=%d high=%d medium=%d low=%d info=%d",
                scanType,
                scanTool,
                status,
                findingCount,
                unresolvedCount,
                criticalCount,
                highCount,
                mediumCount,
                lowCount,
                infoCount);
    }

    private String buildScanCountsJson(String scanType,
            String scannerName,
            String scanTool,
            String scanStatus,
            String severity,
            int findingCount,
            int unresolvedCount,
            int criticalCount,
            int highCount,
            int mediumCount,
            int lowCount,
            int infoCount,
            String branchName,
            String commitSha,
            String workflowRunId,
            String workflowJobName,
            Integer pullRequestNumber) {
        try {
            java.util.Map<String, Object> normalized = new java.util.LinkedHashMap<>();
            normalized.put("scanType", scanType);
            normalized.put("scannerName", scannerName);
            normalized.put("scanTool", scanTool);
            normalized.put("scanStatus", scanStatus);
            normalized.put("severity", severity);
            normalized.put("findingCount", findingCount);
            normalized.put("unresolvedCount", unresolvedCount);
            normalized.put("criticalCount", criticalCount);
            normalized.put("highCount", highCount);
            normalized.put("mediumCount", mediumCount);
            normalized.put("lowCount", lowCount);
            normalized.put("infoCount", infoCount);
            normalized.put("branchName", branchName);
            normalized.put("commitSha", commitSha);
            normalized.put("workflowRunId", workflowRunId);
            normalized.put("workflowJobName", workflowJobName);
            normalized.put("pullRequestNumber", pullRequestNumber);
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            return null;
        }
    }

    private void verifySignature(byte[] rawBody, String signatureHeader) {
        String secret = props.connectors().github().webhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new SecurityException("EDCAP_WEBHOOK_SECRET not configured");
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            throw new SecurityException("Missing or malformed signature");
        }
        String expectedHex = signatureHeader.substring("sha256=".length());
        String actualHex = hmacSha256Hex(secret, rawBody);
        if (!MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.UTF_8),
                actualHex.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("HMAC mismatch");
        }
    }

    private static String hmacSha256Hex(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    private String text(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode child = node.path(key);
            if (child.isTextual() && !child.asText().isBlank()) {
                return child.asText().trim();
            }
        }
        throw new IllegalArgumentException("Missing required field");
    }

    private Integer optionalInteger(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode child = node.path(key);
            if (child.isInt() || child.isLong()) {
                return child.asInt();
            }
            if (child.isTextual()) {
                try {
                    return Integer.parseInt(child.asText().trim());
                } catch (NumberFormatException ignored) {
                    // continue
                }
            }
        }
        return null;
    }

    private int intValue(JsonNode node, String key) {
        JsonNode child = node.path(key);
        if (child.isInt() || child.isLong()) {
            return Math.max(child.asInt(), 0);
        }
        if (child.isTextual()) {
            try {
                return Math.max(Integer.parseInt(child.asText().trim()), 0);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private Boolean booleanValue(JsonNode node, String key) {
        JsonNode child = node.path(key);
        if (child.isBoolean()) {
            return child.asBoolean();
        }
        if (child.isTextual()) {
            String value = child.asText().trim().toLowerCase();
            if ("true".equals(value))
                return Boolean.TRUE;
            if ("false".equals(value))
                return Boolean.FALSE;
        }
        return null;
    }

    private UUID optionalUuid(JsonNode node, String key) {
        JsonNode child = node.path(key);
        if (!child.isTextual() || child.asText().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(child.asText().trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String textOrNull(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode child = node.path(key);
            if (child.isTextual() && !child.asText().isBlank()) {
                return child.asText().trim();
            }
        }
        return null;
    }

    private OffsetDateTime optionalTimestamp(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode child = node.path(key);
            if (child.isTextual()) {
                try {
                    return OffsetDateTime.parse(child.asText().trim());
                } catch (DateTimeParseException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private Linkage resolveLinkage(EvidenceRepository repository,
            String branchName,
            String commitSha,
            Integer pullRequestNumber,
            String workflowRunId,
            String workflowJobId,
            String workflowJobName) {
        UUID ticketId = null;
        UUID prId = null;

        if (pullRequestNumber != null) {
            CiRunModels.PullRequestScope scope = ciRunRepositoryPort
                    .findPullRequestByRepositoryAndExternalNumber(repository.getRepositoryId(), pullRequestNumber)
                    .orElse(null);
            if (scope != null) {
                ticketId = scope.ticketId();
                prId = scope.pullRequestId();
            }
        }
        if (prId == null && branchName != null && !branchName.isBlank()) {
            CiRunModels.PullRequestScope scope = ciRunRepositoryPort
                    .findPullRequestByRepositoryAndBranch(repository.getRepositoryId(), branchName)
                    .orElse(null);
            if (scope != null) {
                ticketId = ticketId != null ? ticketId : scope.ticketId();
                prId = scope.pullRequestId();
            }
        }
        if (prId == null && commitSha != null && !commitSha.isBlank()) {
            CiRunModels.PullRequestScope scope = ciRunRepositoryPort
                    .findPullRequestByRepositoryAndCommit(repository.getRepositoryId(), commitSha)
                    .orElse(null);
            if (scope != null) {
                ticketId = ticketId != null ? ticketId : scope.ticketId();
                prId = scope.pullRequestId();
            }
        }

        UUID ciRunId = null;
        String ciJobIdentity = workflowJobId != null && !workflowJobId.isBlank() ? workflowJobId : workflowJobName;
        if (workflowRunId != null && !workflowRunId.isBlank() && ciJobIdentity != null && !ciJobIdentity.isBlank()) {
            ciRunId = ciRunRepositoryPort
                    .findCiRunIdByIdentity("GITHUB_ACTIONS", repository.getRepositoryId(), workflowRunId)
                    .orElse(null);
        }
        if (ciRunId == null && ticketId != null) {
            ciRunId = ciRunRepositoryPort.findLatestCiRunByRepositoryAndTicket(repository.getRepositoryId(), ticketId)
                    .map(CiRunModels.CiRunMetadataView::ciRunId)
                    .orElse(null);
        }

        return new Linkage(ticketId, prId, ciRunId);
    }

    private String resolveSeverity(int criticalCount, int highCount, int mediumCount, int lowCount, int infoCount) {
        if (criticalCount > 0)
            return "CRITICAL";
        if (highCount > 0)
            return "HIGH";
        if (mediumCount > 0)
            return "MEDIUM";
        if (lowCount > 0)
            return "LOW";
        if (infoCount > 0)
            return "INFO";
        return "INFO";
    }

    private enum ScanPolicy {
        SECRET,
        SAST,
        SCA;

        static ScanPolicy from(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing scanType");
            }
            return ScanPolicy.valueOf(value.trim().toUpperCase());
        }

        String resolveScanStatus(int unresolvedCount, int criticalCount, int highCount) {
            return switch (this) {
                case SECRET -> unresolvedCount > 0 ? "FAIL" : "PASS";
                case SAST, SCA -> criticalCount > 0 ? "FAIL" : highCount > 0 ? "WARNING" : "PASS";
            };
        }
    }

    public record Result(String handled, int recordsAffected) {
    }

    private record Linkage(UUID ticketId, UUID prId, UUID ciRunId) {
    }
}
