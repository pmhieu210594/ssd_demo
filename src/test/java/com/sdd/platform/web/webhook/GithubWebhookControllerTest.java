package com.sdd.platform.web.webhook;

import com.sdd.platform.application.usecase.ingestion.CiRunModels;
import com.sdd.platform.application.usecase.ingestion.GithubWebhookService;
import com.sdd.platform.application.usecase.ingestion.GithubWorkflowJobWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = GithubWebhookControllerTest.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class GithubWebhookControllerTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            ManagementWebSecurityAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class
    })
    @Import(GithubWebhookController.class)
    static class TestApp {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GithubWebhookService githubWebhookService;

    @MockBean
    private GithubWorkflowJobWebhookService githubWorkflowJobWebhookService;

    @Test
    void receive_delegates_workflow_job_events_to_workflow_job_service() throws Exception {
        when(githubWorkflowJobWebhookService.handle(any(), any(), eq("workflow_job"), eq("delivery-1")))
                .thenReturn(new CiRunModels.CollectorResult("workflow_job", 1));

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=test")
                        .header("X-GitHub-Event", "workflow_job")
                        .header("X-GitHub-Delivery", "delivery-1")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handled").value("workflow_job"))
                .andExpect(jsonPath("$.recordsAffected").value(1));

        verify(githubWorkflowJobWebhookService).handle(any(), any(), eq("workflow_job"), eq("delivery-1"));
    }

    @Test
    void receive_returns_unauthorized_for_signature_failures() throws Exception {
        when(githubWorkflowJobWebhookService.handle(any(), any(), eq("workflow_job"), eq("delivery-2")))
                .thenThrow(new SecurityException("HMAC mismatch"));

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=bad")
                        .header("X-GitHub-Event", "workflow_job")
                        .header("X-GitHub-Delivery", "delivery-2")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("signature_invalid"));
    }

    @Test
    void receive_returns_bad_request_for_invalid_payload() throws Exception {
        when(githubWebhookService.handle(any(), any(), eq("push"), eq("delivery-3")))
                .thenThrow(new IllegalArgumentException("bad payload"));

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=test")
                        .header("X-GitHub-Event", "push")
                        .header("X-GitHub-Delivery", "delivery-3")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_payload"));
    }
}
