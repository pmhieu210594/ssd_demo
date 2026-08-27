package com.sdd.platform.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactScanMode;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactScanTriggerType;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactSnapshot;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ScanRun;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerService;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.web.exception.GlobalExceptionHandler;
import com.sdd.platform.web.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ArtifactScannerControllerIntegrationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private ArtifactScannerService service;
    private AppUser admin;
    private AppUser editor;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(ArtifactScannerService.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        admin = user(AppUser.Role.ADMIN);
        editor = user(AppUser.Role.EDITOR);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ArtifactScannerController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver(admin))
                .build();
    }

    @Test
    void run_returns_scan_summary_and_artifact_result_for_admin() throws Exception {
        UUID repositoryId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID connectorId = UUID.randomUUID();
        UUID artifactTypeId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        ScanRun run = new ScanRun(
                runId,
                connectorId,
                OffsetDateTime.parse("2026-06-17T01:00:00Z"),
                OffsetDateTime.parse("2026-06-17T01:01:00Z"),
                "SUCCESS",
                15,
                15,
                null,
                "trace-123");
        ArtifactSnapshot artifact = new ArtifactSnapshot(
                UUID.randomUUID(),
                runId,
                repositoryId,
                ticketId,
                "ARTIFACT-SCANNER",
                "OPEN",
                OffsetDateTime.parse("2026-06-16T06:42:28Z"),
                artifactTypeId,
                UUID.randomUUID(),
                "SPEC_PACK",
                "Spec Pack",
                "spec-pack.md",
                true,
                "1",
                "docs/changes/ARTIFACT-SCANNER/spec-pack.md",
                true,
                "hash-1",
                123L,
                OffsetDateTime.parse("2026-06-16T06:42:28Z"),
                false,
                true,
                "FOUND",
                null,
                OffsetDateTime.parse("2026-06-17T01:01:00Z"));

        when(service.scan(argThat(request -> request != null
                && repositoryId.equals(request.repositoryId())
                && "main".equals(request.branchOrRef())
                && request.scanMode() == ArtifactScanMode.FULL
                && request.triggerType() == ArtifactScanTriggerType.MANUAL
                && "tester".equals(request.requestedBy())
                && "trace-123".equals(request.traceId())))).thenReturn(run);
        when(service.getRunArtifacts(eq(runId))).thenReturn(List.of(artifact));

        mockMvc.perform(post("/api/v1/data-ops/artifact-scans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RequestBody(
                        repositoryId,
                        "main",
                        "FULL",
                        null,
                        "MANUAL",
                        "tester",
                        "trace-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.connectorRunId").value(runId.toString()))
                .andExpect(jsonPath("$.run.status").value("SUCCESS"))
                .andExpect(jsonPath("$.artifacts[0].artifactTypeCode").value("SPEC_PACK"))
                .andExpect(jsonPath("$.artifacts[0].sourcePath").value("docs/changes/ARTIFACT-SCANNER/spec-pack.md"))
                .andExpect(jsonPath("$.artifacts[0].needParse").value(true));

        verify(service).scan(argThat(request -> request != null
                && repositoryId.equals(request.repositoryId())
                && request.scanMode() == ArtifactScanMode.FULL));
        verify(service).getRunArtifacts(runId);
    }

    @Test
    void current_inventory_requires_admin_role() throws Exception {
        MockMvc forbiddenMvc = MockMvcBuilders
                .standaloneSetup(new ArtifactScannerController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver(editor))
                .build();

        forbiddenMvc.perform(get("/api/v1/data-ops/artifact-scans/current")
                .param("repositoryId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Component.Permission.Denied"));
    }

    @Test
    void run_rejects_blank_branch_before_service_call() throws Exception {
        mockMvc.perform(post("/api/v1/data-ops/artifact-scans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RequestBody(
                        UUID.randomUUID(),
                        " ",
                        "FULL",
                        null,
                        "MANUAL",
                        "tester",
                        "trace-123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        Mockito.verifyNoInteractions(service);
    }

    private AppUser user(AppUser.Role role) {
        return AppUser.builder()
                .id(1L)
                .provider("local")
                .providerUid(role.name().toLowerCase())
                .email(role.name().toLowerCase() + "@example.com")
                .displayName(role.name())
                .role(role)
                .active(true)
                .build();
    }

    private record RequestBody(
            UUID repositoryId,
            String branchOrRef,
            String scanMode,
            List<String> ticketIds,
            String triggerType,
            String requestedBy,
            String traceId) {
    }

    private static class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {
        private final AppUser caller;

        private CurrentUserArgumentResolver(AppUser caller) {
            this.caller = caller;
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType().equals(AppUser.class)
                    && parameter.hasParameterAnnotation(CurrentUser.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            return caller;
        }
    }
}
