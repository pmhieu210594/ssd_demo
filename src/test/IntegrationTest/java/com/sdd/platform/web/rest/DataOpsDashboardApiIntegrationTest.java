package com.sdd.platform.web.rest;

import com.sdd.platform.application.port.out.persistence.AuthTokenSessionRepositoryPort;
import com.sdd.platform.application.usecase.dataopsdashboard.DataOpsDashboardModels;
import com.sdd.platform.application.usecase.dataopsdashboard.DataOpsDashboardService;
import com.sdd.platform.application.usecase.governance.AuthTokenService;
import com.sdd.platform.config.AppProperties;
import com.sdd.platform.config.SecurityConfig;
import com.sdd.platform.config.WebMvcConfig;
import com.sdd.platform.domain.model.AuthUserContext;
import com.sdd.platform.web.exception.GlobalExceptionHandler;
import com.sdd.platform.web.security.BearerTokenAuthenticationFilter;
import com.sdd.platform.web.security.CurrentAppUserResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real Spring Security filter chain (SecurityConfig +
 * BearerTokenAuthenticationFilter + CurrentAppUserResolver) in front of
 * DataOpsDashboardController — the coverage gap left by
 * DataOpsDashboardControllerTest, which uses a standalone MockMvc with no
 * security filter at all, so AC-DATAOPS-10 (unauthorized access denied) was
 * never actually exercised end-to-end. Follows the AuthApiIntegrationTest
 * pattern: negative paths (no/invalid token) run through the real filter;
 * the authenticated-success path uses the same
 * {@code .with(authentication(...))} shortcut AuthApiIntegrationTest uses,
 * since BearerTokenAuthenticationFilter's own token-parsing success path is
 * already covered by AuthTokenService's own unit tests.
 */
@SpringBootTest(classes = DataOpsDashboardApiIntegrationTest.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DataOpsDashboardApiIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class
    })
    @Import({
            DataOpsDashboardController.class,
            SecurityConfig.class,
            WebMvcConfig.class,
            CurrentAppUserResolver.class,
            BearerTokenAuthenticationFilter.class,
            GlobalExceptionHandler.class,
            DataOpsDashboardApiIntegrationTest.Config.class
    })
    static class TestApp {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DataOpsDashboardService service;

    @MockBean
    private AuthTokenService authTokenService;

    @MockBean
    private AuthTokenSessionRepositoryPort authTokenSessionRepository;

    @TestConfiguration
    static class Config {
        @Bean
        AppProperties appProperties() {
            return new AppProperties(
                    new AppProperties.Cors(List.of("http://127.0.0.1:5173")),
                    new AppProperties.Frontend("http://127.0.0.1:5173"),
                    new AppProperties.Jwt("test-secret", 2),
                    new AppProperties.Auth(false, false, 0),
                    null
            );
        }
    }

    @Test
    void summary_withoutBearerToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/data-ops/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void summary_withInvalidBearerToken_returns401() throws Exception {
        when(authTokenService.parseAndValidate("bad-token")).thenReturn(null);

        mockMvc.perform(get("/api/v1/data-ops/dashboard/summary")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void summary_withAuthenticatedUser_returnsOkThroughFullSecurityChain() throws Exception {
        AuthUserContext user = authenticatedUser("dataops01", "DATA_OPS");
        when(service.summary(isNull(), isNull(), isNull(), isNull(), isNull(), eq(user)))
                .thenReturn(new DataOpsDashboardModels.DataOpsDashboardSummary(
                        1, 2, 3, 4, 5, OffsetDateTime.parse("2026-07-02T00:00:00Z")
                ));

        mockMvc.perform(get("/api/v1/data-ops/dashboard/summary")
                        .with(authentication(authenticated(user)))
                        .requestAttr("testUserContext", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectorFailureCount", is(1)))
                .andExpect(jsonPath("$.brokenLinkCount", is(5)));
    }

    @Test
    void options_withAuthenticatedUser_returnsOk() throws Exception {
        AuthUserContext user = authenticatedUser("dataops01", "DATA_OPS");
        when(service.options(isNull(), isNull(), eq(user))).thenReturn(
                new DataOpsDashboardModels.DataOpsDashboardOptions(List.of(), List.of(), List.of())
        );

        mockMvc.perform(get("/api/v1/data-ops/dashboard/options")
                        .with(authentication(authenticated(user)))
                        .requestAttr("testUserContext", user))
                .andExpect(status().isOk());
    }

    @Test
    void dashboard_hasNoWriteEndpoint_postToSummaryIsNotAccepted() throws Exception {
        // No @PostMapping exists on DataOpsDashboardController (GET-only, per AC-DATAOPS-10).
        // GlobalExceptionHandler's catch-all Exception handler maps the resulting
        // HttpRequestMethodNotSupportedException to 500 rather than 405 — pre-existing,
        // app-wide behavior (see handleUnknown), not something this ticket changes.
        // What matters for read-only verification is that the request is never routed
        // to a write handler and never succeeds.
        AuthUserContext user = authenticatedUser("dataops01", "DATA_OPS");

        mockMvc.perform(post("/api/v1/data-ops/dashboard/summary")
                        .with(authentication(authenticated(user)))
                        .requestAttr("testUserContext", user))
                .andExpect(status().is5xxServerError());
    }

    private AuthUserContext authenticatedUser(String username, String role) {
        return AuthUserContext.builder()
                .userAccountId(UUID.randomUUID())
                .username(username)
                .displayName("Data Ops User")
                .email("dataops01@example.com")
                .role(role)
                .accessScopes(List.of())
                .build();
    }

    private UsernamePasswordAuthenticationToken authenticated(AuthUserContext user) {
        return new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(() -> "ROLE_" + user.getRole())
        );
    }
}
