package com.sdd.platform.application.usecase.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.integration.ArtifactScannerSourcePort;
import com.sdd.platform.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GithubSecurityEvidenceSnapshotServiceTest {

    private static final String SECRET = "test-secret";

    private ArtifactScannerSourcePort sourcePort;
    private SecurityEvidenceIngestService ingestService;
    private GithubSecurityEvidenceSnapshotService service;

    @BeforeEach
    void setUp() {
        sourcePort = Mockito.mock(ArtifactScannerSourcePort.class);
        ingestService = Mockito.mock(SecurityEvidenceIngestService.class);

        AppProperties.Connectors.GitHub github = new AppProperties.Connectors.GitHub("https://api.github.com", "", SECRET);
        AppProperties.Connectors connectors = new AppProperties.Connectors(null, github, null, null);
        AppProperties props = new AppProperties(null, null, null, null, connectors);

        service = new GithubSecurityEvidenceSnapshotService(props, sourcePort, ingestService, new ObjectMapper());
        when(sourcePort.resolveRevision(anyString(), anyString()))
                .thenReturn(new ArtifactScannerSourcePort.ResolvedRevision("1111111111111111111111111111111111111111", null));
        when(sourcePort.listTree(anyString(), anyString()))
                .thenReturn(Map.of(
                        "documents/.claude/CLAUDE.md", new ArtifactScannerSourcePort.GitHubTreeEntry("documents/.claude/CLAUDE.md", "sha-claude", "blob", 10L),
                        "documents/.claude/settings.json", new ArtifactScannerSourcePort.GitHubTreeEntry("documents/.claude/settings.json", "sha-settings", "blob", 20L),
                        "documents/.claude/rules/00-safety.md", new ArtifactScannerSourcePort.GitHubTreeEntry("documents/.claude/rules/00-safety.md", "sha-rule", "blob", 30L),
                        "reports/security/gitleaks.json", new ArtifactScannerSourcePort.GitHubTreeEntry("reports/security/gitleaks.json", "sha-secret", "blob", 40L),
                        "reports/security/semgrep.json", new ArtifactScannerSourcePort.GitHubTreeEntry("reports/security/semgrep.json", "sha-sast", "blob", 50L),
                        "reports/security/trivy.json", new ArtifactScannerSourcePort.GitHubTreeEntry("reports/security/trivy.json", "sha-sca", "blob", 60L)
                ));
        when(sourcePort.readBlob(anyString(), anyString())).thenAnswer(invocation -> {
            String sha = invocation.getArgument(1);
            return switch (sha) {
                case "sha-claude" -> "# safety".getBytes(StandardCharsets.UTF_8);
                case "sha-settings" -> """
                        {"deny":[1],"ask":[1,2],"allow":[1]}
                        """.getBytes(StandardCharsets.UTF_8);
                case "sha-rule" -> "rule".getBytes(StandardCharsets.UTF_8);
                case "sha-secret" -> """
                        {"findingCount":2,"unresolvedCount":2}
                        """.getBytes(StandardCharsets.UTF_8);
                case "sha-sast" -> """
                        {"findingCount":3,"criticalCount":1,"highCount":2}
                        """.getBytes(StandardCharsets.UTF_8);
                case "sha-sca" -> """
                        {"findingCount":4,"criticalCount":0,"highCount":1}
                        """.getBytes(StandardCharsets.UTF_8);
                default -> "{}".getBytes(StandardCharsets.UTF_8);
            };
        });
        when(ingestService.handle(any(), anyString()))
                .thenReturn(new SecurityEvidenceIngestService.Result("security_evidence", 4));
    }

    @Test
    void collectFromPullRequest_builds_payload_from_path_signals_and_forwards_to_ingest() throws Exception {
        var result = service.collectFromPullRequest(
                "acme/widget",
                "feature/SEC-1",
                "1111111111111111111111111111111111111111",
                42,
                List.of(
                        "documents/.claude/CLAUDE.md",
                        "documents/.claude/settings.json",
                        "documents/.claude/rules/00-safety.md",
                        "reports/security/gitleaks.json",
                        "reports/security/semgrep.json",
                        "reports/security/trivy.json"
                ),
                "delivery-1"
        );

        assertThat(result.handled()).isEqualTo("security_evidence");
        assertThat(result.recordsAffected()).isEqualTo(4);

        ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> sigCaptor = ArgumentCaptor.forClass(String.class);
        verify(ingestService).handle(bodyCaptor.capture(), sigCaptor.capture());

        JsonNode payload = new ObjectMapper().readTree(bodyCaptor.getValue());
        assertThat(payload.path("repositoryNameMasked").asText()).isEqualTo("acme/widget");
        assertThat(payload.path("branchName").asText()).isEqualTo("feature/SEC-1");
        assertThat(payload.path("commitSha").asText()).isEqualTo("1111111111111111111111111111111111111111");
        assertThat(payload.path("pullRequestNumber").asInt()).isEqualTo(42);
        assertThat(payload.path("scans").size()).isEqualTo(3);
        assertThat(payload.path("safetyPack").path("claudeMdExists").asBoolean()).isTrue();
        assertThat(payload.path("safetyPack").path("settingsJsonExists").asBoolean()).isTrue();
        assertThat(payload.path("safetyPack").path("rulesExists").asBoolean()).isTrue();
        assertThat(payload.path("safetyPack").path("denyRuleCount").asInt()).isEqualTo(1);
        assertThat(payload.path("safetyPack").path("askRuleCount").asInt()).isEqualTo(2);
        assertThat(payload.path("safetyPack").path("allowRuleCount").asInt()).isEqualTo(1);
        assertThat(sigCaptor.getValue()).isNotBlank();
    }

    @Test
    void collectFromPullRequest_detects_claude_dir_anywhere_in_tree() throws Exception {
        when(sourcePort.listTree(anyString(), anyString()))
                .thenReturn(Map.of(
                        "services/backend/.claude/CLAUDE.md", new ArtifactScannerSourcePort.GitHubTreeEntry("services/backend/.claude/CLAUDE.md", "sha-claude", "blob", 10L),
                        "services/backend/.claude/settings.json", new ArtifactScannerSourcePort.GitHubTreeEntry("services/backend/.claude/settings.json", "sha-settings", "blob", 20L),
                        "services/backend/.claude/rules/00-safety.md", new ArtifactScannerSourcePort.GitHubTreeEntry("services/backend/.claude/rules/00-safety.md", "sha-rule", "blob", 30L)
                ));

        var result = service.collectFromPullRequest(
                "acme/widget",
                "feature/SEC-1",
                "1111111111111111111111111111111111111111",
                42,
                List.of(),
                "delivery-2"
        );

        assertThat(result.handled()).isEqualTo("security_evidence");

        ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> sigCaptor = ArgumentCaptor.forClass(String.class);
        verify(ingestService).handle(bodyCaptor.capture(), sigCaptor.capture());

        JsonNode payload = new ObjectMapper().readTree(bodyCaptor.getValue());
        assertThat(payload.path("safetyPack").path("claudeMdExists").asBoolean()).isTrue();
        assertThat(payload.path("safetyPack").path("settingsJsonExists").asBoolean()).isTrue();
        assertThat(payload.path("safetyPack").path("rulesExists").asBoolean()).isTrue();
        assertThat(payload.path("safetyPack").path("scanStatus").asText()).isNotEqualTo("MISSING");
        assertThat(payload.path("safetyPack").path("missingItemsSummary").isNull()).isTrue();
        assertThat(sigCaptor.getValue()).startsWith("sha256=");
    }
}
