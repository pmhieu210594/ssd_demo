package com.sdd.platform.web.rest;

import com.sdd.platform.application.port.out.persistence.SecurityScanRepositoryPort;
import com.sdd.platform.application.usecase.governance.SafetyPackService;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.SafetyPackStatus;
import com.sdd.platform.web.exception.GlobalExceptionHandler;
import com.sdd.platform.web.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SecurityEvidenceControllerIntegrationTest.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SecurityEvidenceControllerIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            ManagementWebSecurityAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class
    })
    @org.springframework.context.annotation.Import({
            SecurityEvidenceController.class,
            GlobalExceptionHandler.class,
            SecurityEvidenceControllerIntegrationTest.Config.class
    })
    static class TestApp {
    }

    @TestConfiguration
    static class Config {
        @Bean
        WebMvcConfigurer testCurrentUserResolver() {
            return new WebMvcConfigurer() {
                @Override
                public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                    resolvers.add(new HandlerMethodArgumentResolver() {
                        @Override
                        public boolean supportsParameter(MethodParameter parameter) {
                            return parameter.getParameterType().equals(AppUser.class)
                                    && parameter.hasParameterAnnotation(CurrentUser.class);
                        }

                        @Override
                        public Object resolveArgument(MethodParameter parameter,
                                                      ModelAndViewContainer mavContainer,
                                                      NativeWebRequest webRequest,
                                                      WebDataBinderFactory binderFactory) {
                            return webRequest.getAttribute("testAppUser", NativeWebRequest.SCOPE_REQUEST);
                        }
                    });
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SafetyPackService safetyPackService;

    @MockBean
    private SecurityScanRepositoryPort securityScanRepositoryPort;

    private AppUser viewer;

    @BeforeEach
    void setUp() {
        viewer = user(AppUser.Role.VIEWER, "viewer@example.com");
    }

    @Test
    void listSafetyPacks_returnsRowsForAuthenticatedUser() throws Exception {
        when(safetyPackService.recent(eq(30), eq(viewer))).thenReturn(List.of(
                SafetyPackStatus.builder()
                        .safetyPackStatusId(UUID.fromString("00000000-0000-0000-0000-000000000101"))
                        .repositoryId(UUID.fromString("00000000-0000-0000-0000-000000000102"))
                        .repositoryNameMasked("org/repo")
                        .claudeMdExists(true)
                        .settingsJsonExists(true)
                        .rulesExists(true)
                        .denyRuleCount(1)
                        .askRuleCount(2)
                        .allowRuleCount(3)
                        .reviewedFlag(Boolean.FALSE)
                        .reviewedByRoleId(null)
                        .lastUpdatedAt(OffsetDateTime.parse("2026-06-01T00:00:00Z"))
                        .collectedAt(OffsetDateTime.parse("2026-06-01T00:00:00Z"))
                        .branchName("main")
                        .commitSha("abc123")
                        .scanStatus("READY")
                        .settingsParseStatus("OK")
                        .contentHash("hash-1")
                        .missingItemsSummary(null)
                        .createdAt(OffsetDateTime.parse("2026-06-01T00:00:00Z"))
                        .createdBy("SYSTEM")
                        .updatedAt(OffsetDateTime.parse("2026-06-01T00:00:00Z"))
                        .updatedBy("SYSTEM")
                        .build()
        ));

        mockMvc.perform(get("/api/v1/admin/safety-packs")
                        .requestAttr("testAppUser", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].safetyPackStatusId", is("00000000-0000-0000-0000-000000000101")))
                .andExpect(jsonPath("$[0].scanStatus", is("READY")))
                .andExpect(jsonPath("$[0].repositoryNameMasked", is("org/repo")));
    }

    @Test
    void securityScans_returnsRowsForAuthenticatedUser() throws Exception {
        when(securityScanRepositoryPort.findRecent(eq(30))).thenReturn(List.of(
                com.sdd.platform.domain.model.SecurityScan.builder()
                        .securityScanId(UUID.fromString("00000000-0000-0000-0000-000000000301"))
                        .repositoryId(UUID.fromString("00000000-0000-0000-0000-000000000302"))
                        .repositoryNameMasked("org/repo")
                        .pullRequestNumber(12)
                        .branchName("feature/test")
                        .commitSha("abc123")
                        .workflowRunId("run-1")
                        .workflowJobName("Security Evidence Scan")
                        .scannerType("SECRET")
                        .scannerName("Gitleaks")
                        .scanTool("gitleaks")
                        .status("SUCCESS")
                        .scanStatus("PASS")
                        .severity("LOW")
                        .findingCount(0)
                        .unresolvedCount(0)
                        .criticalCount(0)
                        .highCount(0)
                        .mediumCount(0)
                        .lowCount(0)
                        .infoCount(0)
                        .summary("ok")
                        .scanCountsJson("{}")
                        .startedAt(OffsetDateTime.parse("2026-06-01T00:00:00Z"))
                        .finishedAt(OffsetDateTime.parse("2026-06-01T00:01:00Z"))
                        .collectedAt(OffsetDateTime.parse("2026-06-01T00:02:00Z"))
                        .build()
        ));

        mockMvc.perform(get("/api/v1/admin/security-scans")
                        .requestAttr("testAppUser", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].securityScanId", is("00000000-0000-0000-0000-000000000301")))
                .andExpect(jsonPath("$[0].scanStatus", is("PASS")))
                .andExpect(jsonPath("$[0].repositoryNameMasked", is("org/repo")));
    }

    private AppUser user(AppUser.Role role, String email) {
        return AppUser.builder()
                .id(1L)
                .provider("google")
                .providerUid(email)
                .email(email)
                .displayName(email)
                .role(role)
                .active(true)
                .build();
    }
}
