package com.sdd.platform.web.rest;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.exception.OptimisticLockingException;
import com.sdd.platform.application.usecase.quality.ScoreThresholdConfigModels.ScoreThreshold;
import com.sdd.platform.application.usecase.quality.ScoreThresholdConfigService;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.web.exception.GlobalExceptionHandler;
import com.sdd.platform.web.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScoreThresholdConfigControllerTest {

    private MockMvc mockMvc;
    private ScoreThresholdConfigService service;
    private AppUser admin;
    private AppUser viewer;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(ScoreThresholdConfigService.class);
        admin = AppUser.builder().email("admin@example.com").role(AppUser.Role.ADMIN).active(true).build();
        viewer = AppUser.builder().email("viewer@example.com").role(AppUser.Role.VIEWER).active(true).build();
        mockMvc = MockMvcBuilders.standaloneSetup(new ScoreThresholdConfigController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedCurrentUserResolver(admin))
                .build();
    }

    @Test
    void list_returnsActiveBands() throws Exception {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        OffsetDateTime now = OffsetDateTime.now();
        when(service.list(admin)).thenReturn(List.of(
                new ScoreThreshold(id, "EXCELLENT", "Excellent", 90, 100, "#10B981", now, "SYSTEM", now, "SYSTEM")));

        mockMvc.perform(get("/api/v1/score-thresholds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("EXCELLENT"))
                .andExpect(jsonPath("$[0].minScore").value(90))
                .andExpect(jsonPath("$[0].maxScore").value(100));
    }

    @Test
    void list_rejectsNonAdminWith403() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(new ScoreThresholdConfigController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedCurrentUserResolver(viewer))
                .build();
        when(service.list(viewer)).thenThrow(new ForbiddenException("Component.Permission.Denied"));

        mockMvc.perform(get("/api/v1/score-thresholds"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void save_returnsUpdatedActiveBands() throws Exception {
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        OffsetDateTime now = OffsetDateTime.now();
        when(service.save(any(), any())).thenReturn(List.of(
                new ScoreThreshold(id, "ALL", "All", 0, 100, "#10B981", now, "SYSTEM", now, "SYSTEM")));

        mockMvc.perform(post("/api/v1/score-thresholds")
                        .contentType("application/json")
                        .content("{\"thresholds\":[{\"code\":\"ALL\",\"label\":\"All\",\"minScore\":0,\"maxScore\":100,\"color\":\"#10B981\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("ALL"));
    }

    @Test
    void save_rejectsNonAdminWith403() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(new ScoreThresholdConfigController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedCurrentUserResolver(viewer))
                .build();
        when(service.save(any(), any())).thenThrow(new ForbiddenException("Component.Permission.Denied"));

        mockMvc.perform(post("/api/v1/score-thresholds")
                        .contentType("application/json")
                        .content("{\"thresholds\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void save_rejectsInvalidPayloadWith400() throws Exception {
        when(service.save(any(), any())).thenThrow(new BusinessRuleException("Pages.ThresholdConfig.Coverage.Gap"));

        mockMvc.perform(post("/api/v1/score-thresholds")
                        .contentType("application/json")
                        .content("{\"thresholds\":[{\"code\":\"A\",\"label\":\"A\",\"minScore\":0,\"maxScore\":39,\"color\":\"#10B981\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DOMAIN_RULE_VIOLATION"));
    }

    @Test
    void save_returnsConflictOnOptimisticLockFailure() throws Exception {
        when(service.save(any(), any())).thenThrow(new OptimisticLockingException("Pages.ThresholdConfig.Conflict.Version"));

        mockMvc.perform(post("/api/v1/score-thresholds")
                        .contentType("application/json")
                        .content("{\"thresholds\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    private static final class FixedCurrentUserResolver implements HandlerMethodArgumentResolver {
        private final AppUser caller;

        private FixedCurrentUserResolver(AppUser caller) {
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
