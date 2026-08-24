package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.CollectorRunResult;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.RepositoryScanRequest;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorService;
import com.sdd.platform.config.WebMvcConfig;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.AuthUserContext;
import com.sdd.platform.web.security.CurrentAppUserResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = GitPrMetadataCollectorControllerTest.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class GitPrMetadataCollectorControllerTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            ManagementWebSecurityAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class
    })
    @Import({
            GitPrMetadataCollectorController.class,
            WebMvcConfig.class,
            CurrentAppUserResolver.class
    })
    static class TestApp {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GitPrMetadataCollectorService service;

    @Test
    void repository_level_collect_returns_response_for_admin() throws Exception {
        when(service.collectManual(any(RepositoryScanRequest.class), any(AppUser.class)))
                .thenReturn(new CollectorRunResult(
                        UUID.fromString("2c03a4a5-0a3f-4d50-9ea0-5a8ad2f1b0f7"),
                        UUID.fromString("99e85aef-8cf4-4b3c-8863-90e2740e7549"),
                        null,
                        "SUCCESS",
                        1,
                        1,
                        1,
                        0,
                        "trace-1"
                ));

        AuthUserContext admin = AuthUserContext.builder()
                .userAccountId(UUID.randomUUID())
                .username("admin")
                .displayName("Admin")
                .email("admin@example.com")
                .role("ADMIN")
                .accessScopes(List.of())
                .build();
        var authentication = new UsernamePasswordAuthenticationToken(admin, null, List.of(() -> "ROLE_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            mockMvc.perform(post("/api/v1/repositories/{repositoryId}/git-pr-metadata/collect",
                            "99e85aef-8cf4-4b3c-8863-90e2740e7549")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"fromDate":"2026-06-17","toDate":"2026-06-18","includeClosed":true}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.repositoryId").value("99e85aef-8cf4-4b3c-8863-90e2740e7549"))
                    .andExpect(jsonPath("$.processedPrCount").value(1));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
