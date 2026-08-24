package com.sdd.platform.application.usecase.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.persistence.EvidenceRepositoryPort;
import com.sdd.platform.application.port.out.persistence.SafetyPackStatusRepositoryPort;
import com.sdd.platform.domain.model.EvidenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SafetyPackServiceTest {

    @TempDir
    Path tempDir;

    private EvidenceRepositoryPort repositoryPort;
    private SafetyPackStatusRepositoryPort statusRepositoryPort;
    private SafetyPackService service;

    @BeforeEach
    void setUp() {
        repositoryPort = Mockito.mock(EvidenceRepositoryPort.class);
        statusRepositoryPort = Mockito.mock(SafetyPackStatusRepositoryPort.class);
        service = new SafetyPackService(repositoryPort, statusRepositoryPort, new ObjectMapper());
    }

    @Test
    void scan_prefersDocumentsClaudeWhenBothSourceTreesExist() throws Exception {
        Path documentsClaude = tempDir.resolve("documents").resolve(".claude");
        Path rootClaude = tempDir.resolve(".claude");

        Files.createDirectories(documentsClaude.resolve("rules"));
        Files.createDirectories(rootClaude.resolve("rules"));

        Files.writeString(documentsClaude.resolve("CLAUDE.md"), "# documents");
        Files.writeString(documentsClaude.resolve("settings.json"), """
                {
                  "deny": [1],
                  "ask": [1, 2],
                  "allow": [1]
                }
                """);
        Files.writeString(documentsClaude.resolve("rules").resolve("rule-a.md"), "documents rule");

        Files.writeString(rootClaude.resolve("CLAUDE.md"), "# root");
        Files.writeString(rootClaude.resolve("settings.json"), """
                {
                  "deny": [1, 2, 3],
                  "ask": [1, 2, 3],
                  "allow": [1, 2, 3]
                }
                """);
        Files.writeString(rootClaude.resolve("rules").resolve("rule-b.md"), "root rule");

        EvidenceRepository repository = repository();

        var result = service.scan(repository, tempDir);

        assertThat(result.getRepositoryNameMasked()).isEqualTo("org/repo");
        assertThat(result.getScanStatus()).isEqualTo("READY");
        assertThat(result.getSettingsParseStatus()).isEqualTo("OK");
        assertThat(result.getClaudeMdExists()).isTrue();
        assertThat(result.getSettingsJsonExists()).isTrue();
        assertThat(result.getRulesExists()).isTrue();
        assertThat(result.getDenyRuleCount()).isEqualTo(1);
        assertThat(result.getAskRuleCount()).isEqualTo(2);
        assertThat(result.getAllowRuleCount()).isEqualTo(1);
        assertThat(result.getMissingItemsSummary()).isNull();
    }

    @Test
    void scan_marksParseErrorWhenSettingsJsonIsInvalid() throws Exception {
        Path documentsClaude = tempDir.resolve("documents").resolve(".claude");
        Files.createDirectories(documentsClaude.resolve("rules"));

        Files.writeString(documentsClaude.resolve("CLAUDE.md"), "# documents");
        Files.writeString(documentsClaude.resolve("settings.json"), "{ invalid json");
        Files.writeString(documentsClaude.resolve("rules").resolve("rule-a.md"), "documents rule");

        EvidenceRepository repository = repository();

        var result = service.scan(repository, tempDir);

        assertThat(result.getScanStatus()).isEqualTo("PARSE_ERROR");
        assertThat(result.getSettingsParseStatus()).isEqualTo("ERROR");
        assertThat(result.getDenyRuleCount()).isZero();
        assertThat(result.getAskRuleCount()).isZero();
        assertThat(result.getAllowRuleCount()).isZero();
    }

    private EvidenceRepository repository() {
        return EvidenceRepository.builder()
                .repositoryId(UUID.fromString("00000000-0000-0000-0000-000000000101"))
                .repositoryNameMasked("org/repo")
                .hostType("GITHUB")
                .build();
    }
}
