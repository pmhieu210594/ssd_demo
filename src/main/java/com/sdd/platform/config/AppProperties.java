package com.sdd.platform.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Typed configuration for everything under `app.*` in application.yml.
 * Spring Boot validates and binds these at startup, fails fast on missing required values.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Cors cors,
        Frontend frontend,
        Jwt jwt,
        Auth auth,
        Connectors connectors
) {
    public record Cors(List<String> allowedOrigins) {}

    public record Frontend(String baseUrl) {}

    public record Jwt(@NotBlank String secret, int expirationHours) {}

    public record Auth(
            boolean enableRefreshToken,
            boolean enableRememberMe,
            int rememberMeTtlDays
    ) {}

    public record Connectors(
            GitLocal gitLocal,
            GitHub github,
            Jira jira,
            CircleCi circleci
    ) {
        public record GitLocal(String rootPath) {}
        /** apiToken for PULL (background sync), webhookSecret for PUSH (incoming webhooks). */
        public record GitHub(String apiBaseUrl, String apiToken, String webhookSecret) {}
        public record Jira(String baseUrl, String email, String apiToken) {}
        public record CircleCi(String apiBaseUrl, String apiToken, String webhookSecret) {}
    }
}
