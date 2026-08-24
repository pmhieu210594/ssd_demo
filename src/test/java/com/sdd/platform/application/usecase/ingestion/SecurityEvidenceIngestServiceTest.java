package com.sdd.platform.application.usecase.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.persistence.CiRunRepositoryPort;
import com.sdd.platform.application.port.out.persistence.EvidenceRepositoryPort;
import com.sdd.platform.application.port.out.persistence.SafetyPackStatusRepositoryPort;
import com.sdd.platform.application.port.out.persistence.SecurityScanRepositoryPort;
import com.sdd.platform.config.AppProperties;
import com.sdd.platform.domain.model.EvidenceRepository;
import com.sdd.platform.domain.model.SafetyPackStatus;
import com.sdd.platform.domain.model.SecurityScan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityEvidenceIngestServiceTest {

    private static final String SECRET = "test-webhook-secret";

    private SecurityEvidenceIngestService service;
    private EvidenceRepositoryPort repositoryPort;
    private CiRunRepositoryPort ciRunRepositoryPort;
    private SafetyPackStatusRepositoryPort safetyPackStatusRepositoryPort;
    private SecurityScanRepositoryPort scanRepositoryPort;

    @BeforeEach
    void setUp() {
        repositoryPort = Mockito.mock(EvidenceRepositoryPort.class);
        ciRunRepositoryPort = Mockito.mock(CiRunRepositoryPort.class);
        safetyPackStatusRepositoryPort = Mockito.mock(SafetyPackStatusRepositoryPort.class);
        scanRepositoryPort = Mockito.mock(SecurityScanRepositoryPort.class);

        AppProperties.Connectors.GitHub github =
                new AppProperties.Connectors.GitHub("https://api.github.com", "", SECRET);
        AppProperties.Connectors connectors = new AppProperties.Connectors(null, github, null, null);
        AppProperties props = new AppProperties(null, null, null, null, connectors);

        service = new SecurityEvidenceIngestService(
                props,
                new ObjectMapper(),
                repositoryPort,
                ciRunRepositoryPort,
                safetyPackStatusRepositoryPort,
                scanRepositoryPort
        );

        when(repositoryPort.findByRepositoryNameMaskedAndHostType("pdkhoa2505/Test_CI", "GITHUB"))
                .thenReturn(Optional.of(repository()));
        when(ciRunRepositoryPort.findPullRequestByRepositoryAndExternalNumber(UUID.fromString("00000000-0000-0000-0000-000000000101"), 12))
                .thenReturn(Optional.of(new CiRunModels.PullRequestScope(
                        UUID.fromString("00000000-0000-0000-0000-000000000401"),
                        UUID.fromString("00000000-0000-0000-0000-000000000301"),
                        12,
                        "feature/test"
                )));
        when(ciRunRepositoryPort.findCiRunIdByIdentity("GITHUB_ACTIONS", UUID.fromString("00000000-0000-0000-0000-000000000101"), "27661036403"))
                .thenReturn(Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000201")));
    }

    @Test
    void handle_persists_security_scans_and_safety_pack_from_payload() throws Exception {
        byte[] body = """
                {
                  "repositoryNameMasked": "pdkhoa2505/Test_CI",
                  "branchName": "feature/test",
                  "commitSha": "b2c2ca2306a749aecfa44aa68c30ebd34d294d57",
	                  "pullRequestNumber": 12,
	                  "workflowRunId": "27661036403",
	                  "workflowJobId": "918273645",
	                  "workflowJobName": "Security Evidence Scan",
                  "scans": [
                    {
                      "scanType": "SECRET",
                      "scannerName": "Gitleaks",
                      "scanTool": "gitleaks",
                      "scanStatus": "PASS",
                      "findingCount": 0,
                      "unresolvedCount": 0,
                      "criticalCount": 0,
                      "highCount": 0,
                      "mediumCount": 0,
                      "lowCount": 0,
                      "infoCount": 0
                    }
                  ],
                  "safetyPack": {
                    "repositoryNameMasked": "pdkhoa2505/Test_CI",
                    "branchName": "feature/test",
                    "commitSha": "b2c2ca2306a749aecfa44aa68c30ebd34d294d57",
                    "claudeMdExists": true,
                    "settingsJsonExists": true,
                    "rulesExists": true,
                    "denyRuleCount": 1,
                    "askRuleCount": 2,
                    "allowRuleCount": 3,
                    "reviewedFlag": false,
                    "lastUpdatedAt": "2026-06-17T02:09:10Z",
                    "collectedAt": "2026-06-17T02:09:10Z",
                    "scanStatus": "READY",
                    "settingsParseStatus": "OK",
                    "contentHash": "abc123",
                    "missingItemsSummary": null,
                    "createdAt": "2026-06-17T02:09:10Z",
                    "createdBy": "SYSTEM",
                    "updatedAt": "2026-06-17T02:09:10Z",
                    "updatedBy": "SYSTEM"
                  }
                }
                """.getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=" + hmac(SECRET, body);

        var result = service.handle(body, signature);

        assertThat(result.handled()).isEqualTo("security_evidence");
        assertThat(result.recordsAffected()).isEqualTo(2);
        ArgumentCaptor<SecurityScan> scanCaptor = ArgumentCaptor.forClass(SecurityScan.class);
        verify(scanRepositoryPort).save(scanCaptor.capture());
        assertThat(scanCaptor.getValue().getTicketId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000301"));
        assertThat(scanCaptor.getValue().getPrId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000401"));
        assertThat(scanCaptor.getValue().getCiRunId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000201"));
        verify(safetyPackStatusRepositoryPort).save(any(SafetyPackStatus.class));
    }

    @Test
    void handle_ignores_missing_safety_pack_section() throws Exception {
        byte[] body = """
                {
                  "repositoryNameMasked": "pdkhoa2505/Test_CI",
                  "branchName": "feature/test",
                  "commitSha": "b2c2ca2306a749aecfa44aa68c30ebd34d294d57",
                  "workflowRunId": "27661036403",
                  "workflowJobName": "Security Evidence Scan",
                  "scans": [
                    {
                      "scanType": "SECRET",
                      "scannerName": "Gitleaks",
                      "scanTool": "gitleaks",
                      "scanStatus": "PASS",
                      "findingCount": 0,
                      "unresolvedCount": 0,
                      "criticalCount": 0,
                      "highCount": 0,
                      "mediumCount": 0,
                      "lowCount": 0,
                      "infoCount": 0
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=" + hmac(SECRET, body);

        var result = service.handle(body, signature);

        assertThat(result.recordsAffected()).isEqualTo(1);
        verify(scanRepositoryPort).save(any(SecurityScan.class));
        verify(safetyPackStatusRepositoryPort, Mockito.never()).save(any());
    }

    private static EvidenceRepository repository() {
        return EvidenceRepository.builder()
                .repositoryId(UUID.fromString("00000000-0000-0000-0000-000000000101"))
                .repositoryNameMasked("pdkhoa2505/Test_CI")
                .hostType("github")
                .build();
    }

    private static String hmac(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
