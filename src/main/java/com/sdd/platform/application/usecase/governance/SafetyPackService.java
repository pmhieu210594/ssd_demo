package com.sdd.platform.application.usecase.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.EvidenceRepositoryPort;
import com.sdd.platform.application.port.out.persistence.SafetyPackStatusRepositoryPort;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.EvidenceRepository;
import com.sdd.platform.domain.model.SafetyPackStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class SafetyPackService {

    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 100;

    private final EvidenceRepositoryPort repositoryPort;
    private final SafetyPackStatusRepositoryPort statusRepositoryPort;
    private final ObjectMapper objectMapper;

    public SafetyPackService(EvidenceRepositoryPort repositoryPort,
                             SafetyPackStatusRepositoryPort statusRepositoryPort,
                             ObjectMapper objectMapper) {
        this.repositoryPort = repositoryPort;
        this.statusRepositoryPort = statusRepositoryPort;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<SafetyPackStatus> recent(int limit, AppUser caller) {
        requireAuthenticated(caller);
        return statusRepositoryPort.findRecent(normalizeLimit(limit));
    }

    @Transactional
    public int scanFromRoot(Path repositoryRoot) {
        String repositoryNameMasked = resolveRepositoryNameMasked(repositoryRoot);
        EvidenceRepository repository = repositoryPort
                .findByRepositoryNameMaskedAndHostType(repositoryNameMasked, "GITHUB")
                .orElseGet(() -> repositoryPort
                        .findByRepositoryNameMaskedAndHostType(repositoryRoot.getFileName().toString(), "LOCAL")
                        .orElse(null));
        if (repository == null) {
            return 0;
        }
        SafetyPackStatus status = scan(repository, repositoryRoot);
        statusRepositoryPort.save(status);
        return 1;
    }

    public SafetyPackStatus scan(EvidenceRepository repository, Path repositoryRoot) {
        OffsetDateTime now = OffsetDateTime.now();
        Path sourceDir = resolveSourceDir(repositoryRoot).orElse(null);

        boolean claudeMdExists = sourceDir != null && Files.isRegularFile(sourceDir.resolve("CLAUDE.md"));
        boolean settingsJsonExists = sourceDir != null && Files.isRegularFile(sourceDir.resolve("settings.json"));
        boolean rulesExists = sourceDir != null && Files.isDirectory(sourceDir.resolve("rules"));

        SettingsSummary summary = parseSettings(sourceDir);
        int rulesCount = countMarkdownRules(sourceDir);
        String missingItemsSummary = buildMissingItemsSummary(claudeMdExists, settingsJsonExists, rulesExists, rulesCount);
        String scanStatus = computeScanStatus(sourceDir, summary.parseStatus, claudeMdExists, settingsJsonExists, rulesExists, rulesCount);
        String contentHash = computeContentHash(sourceDir);

        return SafetyPackStatus.builder()
                .safetyPackStatusId(UUID.randomUUID())
                .repositoryId(repository.getRepositoryId())
                .repositoryNameMasked(repository.getRepositoryNameMasked())
                .claudeMdExists(claudeMdExists)
                .settingsJsonExists(settingsJsonExists)
                .rulesExists(rulesExists)
                .denyRuleCount(summary.denyCount)
                .askRuleCount(summary.askCount)
                .allowRuleCount(summary.allowCount)
                .reviewedFlag(Boolean.FALSE)
                .reviewedByRoleId(null)
                .lastUpdatedAt(now)
                .collectedAt(now)
                .branchName(resolveGitBranch(repositoryRoot))
                .commitSha(resolveGitCommitSha(repositoryRoot))
                .scanStatus(scanStatus)
                .settingsParseStatus(summary.parseStatus)
                .contentHash(contentHash)
                .missingItemsSummary(missingItemsSummary)
                .createdAt(now)
                .createdBy("SYSTEM")
                .updatedAt(now)
                .updatedBy("SYSTEM")
                .build();
    }

    private Optional<Path> resolveSourceDir(Path repositoryRoot) {
        Path documentsClaude = repositoryRoot.resolve("documents").resolve(".claude");
        if (Files.isDirectory(documentsClaude)) {
            return Optional.of(documentsClaude);
        }
        Path rootClaude = repositoryRoot.resolve(".claude");
        if (Files.isDirectory(rootClaude)) {
            return Optional.of(rootClaude);
        }
        return Optional.empty();
    }

    private SettingsSummary parseSettings(Path sourceDir) {
        if (sourceDir == null) {
            return SettingsSummary.missing();
        }
        Path settingsFile = sourceDir.resolve("settings.json");
        if (!Files.isRegularFile(settingsFile)) {
            return SettingsSummary.missing();
        }
        try {
            JsonNode root = objectMapper.readTree(Files.readString(settingsFile, StandardCharsets.UTF_8));
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

    private int countMarkdownRules(Path sourceDir) {
        if (sourceDir == null) {
            return 0;
        }
        Path rulesDir = sourceDir.resolve("rules");
        if (!Files.isDirectory(rulesDir)) {
            return 0;
        }
        try (var stream = Files.walk(rulesDir, 1)) {
            return (int) stream
                    .filter(path -> !path.equals(rulesDir))
                    .filter(path -> Files.isRegularFile(path))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                    .count();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private String buildMissingItemsSummary(boolean claudeMdExists,
                                            boolean settingsJsonExists,
                                            boolean rulesExists,
                                            int rulesCount) {
        List<String> missing = new ArrayList<>();
        if (!claudeMdExists) missing.add("CLAUDE.md");
        if (!settingsJsonExists) missing.add("settings.json");
        if (!rulesExists) {
            missing.add("rules/");
        } else if (rulesCount == 0) {
            missing.add("rules/*.md");
        }
        return missing.isEmpty() ? null : String.join(", ", missing);
    }

    private String computeScanStatus(Path sourceDir,
                                     String parseStatus,
                                     boolean claudeMdExists,
                                     boolean settingsJsonExists,
                                     boolean rulesExists,
                                     int rulesCount) {
        if (sourceDir == null) {
            return "MISSING";
        }
        if ("ERROR".equals(parseStatus)) {
            return "PARSE_ERROR";
        }
        if (claudeMdExists && settingsJsonExists && rulesExists && rulesCount > 0) {
            return "READY";
        }
        return "WARNING";
    }

    private String computeContentHash(Path sourceDir) {
        if (sourceDir == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Path claudeMd = sourceDir.resolve("CLAUDE.md");
            Path settingsJson = sourceDir.resolve("settings.json");
            updateDigest(digest, claudeMd);
            updateDigest(digest, settingsJson);
            Path rulesDir = sourceDir.resolve("rules");
            if (Files.isDirectory(rulesDir)) {
                try (var stream = Files.walk(rulesDir)) {
                    stream.filter(Files::isRegularFile)
                            .sorted()
                            .forEach(path -> updateDigest(digest, path));
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException ex) {
            return null;
        }
    }

    private void updateDigest(MessageDigest digest, Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        try {
            digest.update(path.getFileName().toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Files.readAllBytes(path));
            digest.update((byte) 0);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private String resolveGitBranch(Path repositoryRoot) {
        Path head = repositoryRoot.resolve(".git").resolve("HEAD");
        if (!Files.isRegularFile(head)) {
            return null;
        }
        try {
            String value = Files.readString(head, StandardCharsets.UTF_8).trim();
            if (value.startsWith("ref: ")) {
                String ref = value.substring("ref: ".length()).trim();
                int slash = ref.lastIndexOf('/');
                return slash >= 0 ? ref.substring(slash + 1) : ref;
            }
            return null;
        } catch (IOException ex) {
            return null;
        }
    }

    private String resolveGitCommitSha(Path repositoryRoot) {
        Path head = repositoryRoot.resolve(".git").resolve("HEAD");
        if (!Files.isRegularFile(head)) {
            return null;
        }
        try {
            String value = Files.readString(head, StandardCharsets.UTF_8).trim();
            if (value.startsWith("ref: ")) {
                String ref = value.substring("ref: ".length()).trim();
                Path refFile = repositoryRoot.resolve(".git").resolve(ref);
                if (Files.isRegularFile(refFile)) {
                    return Files.readString(refFile, StandardCharsets.UTF_8).trim();
                }
                return null;
            }
            return value.isBlank() ? null : value;
        } catch (IOException ex) {
            return null;
        }
    }

    private String resolveRepositoryNameMasked(Path repositoryRoot) {
        Path config = repositoryRoot.resolve(".git").resolve("config");
        if (Files.isRegularFile(config)) {
            try {
                List<String> lines = Files.readAllLines(config, StandardCharsets.UTF_8);
                boolean inOrigin = false;
                for (String rawLine : lines) {
                    String line = rawLine.trim();
                    if (line.startsWith("[")) {
                        inOrigin = "[remote \"origin\"]".equalsIgnoreCase(line);
                        continue;
                    }
                    if (inOrigin && line.startsWith("url =")) {
                        return normalizeGitRemoteUrl(line.substring(5).trim());
                    }
                }
            } catch (IOException ignored) {
                // Fall back to folder name.
            }
        }
        return repositoryRoot.getFileName().toString();
    }

    private String normalizeGitRemoteUrl(String url) {
        String normalized = url;
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        int githubIndex = normalized.indexOf("github.com/");
        if (githubIndex >= 0) {
            normalized = normalized.substring(githubIndex + "github.com/".length());
        }
        int colonIndex = normalized.lastIndexOf(':');
        if (colonIndex >= 0 && colonIndex < normalized.length() - 1) {
            normalized = normalized.substring(colonIndex + 1);
        }
        normalized = normalized.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.isBlank() ? normalized : normalized;
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
            var direct = node.get(key);
            if (direct != null) {
                return direct;
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                JsonNode child = entry.getValue();
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

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private void requireAuthenticated(AppUser caller) {
        if (caller == null) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    private record SettingsSummary(int denyCount, int askCount, int allowCount, String parseStatus) {
        static SettingsSummary missing() {
            return new SettingsSummary(0, 0, 0, "MISSING");
        }

        static SettingsSummary error() {
            return new SettingsSummary(0, 0, 0, "ERROR");
        }
    }
}
