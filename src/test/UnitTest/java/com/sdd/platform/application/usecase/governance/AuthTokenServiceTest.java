package com.sdd.platform.application.usecase.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.config.AppProperties;
import com.sdd.platform.domain.model.AuthUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthTokenServiceTest {

    private AuthTokenService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                null,
                null,
                new AppProperties.Jwt("test-secret", 2),
                new AppProperties.Auth(false, false, 0),
                null
        );
        service = new AuthTokenService(props, new ObjectMapper());
    }

    @Test
    void issue_and_parse_roundtrip_preserves_user_identity() {
        AuthUserContext user = AuthUserContext.builder()
                .userAccountId(UUID.randomUUID())
                .username("alice")
                .displayName("Alice Example")
                .email("alice@example.com")
                .role("ADMIN")
                .accessScopes(List.of("role:1", "scope:write"))
                .build();

        String token = service.issueToken(user, 60);
        AuthUserContext parsed = service.parseAndValidate(token);

        assertEquals(user.getUsername(), parsed.getUsername());
        assertEquals(user.getDisplayName(), parsed.getDisplayName());
        assertEquals(user.getEmail(), parsed.getEmail());
        assertEquals(user.getRole(), parsed.getRole());
        assertEquals(user.getAccessScopes(), parsed.getAccessScopes());
        assertEquals(user.getUserAccountId(), parsed.getUserAccountId());
    }

    @Test
    void parse_rejects_expired_token() {
        AuthUserContext user = AuthUserContext.builder()
                .userAccountId(UUID.randomUUID())
                .username("alice")
                .displayName("Alice Example")
                .email("alice@example.com")
                .role("VIEWER")
                .accessScopes(List.of())
                .build();

        String token = service.issueToken(user, 0);

        assertNull(service.parseAndValidate(token));
    }

    @Test
    void parse_rejects_tampered_token_payload() {
        AuthUserContext user = AuthUserContext.builder()
                .userAccountId(UUID.randomUUID())
                .username("alice")
                .displayName("Alice Example")
                .email("alice@example.com")
                .role("VIEWER")
                .accessScopes(List.of())
                .build();

        String token = service.issueToken(user, 60);
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1].substring(0, parts[1].length() - 1) + "A." + parts[2];

        assertNull(service.parseAndValidate(tamperedToken));
    }

    @Test
    void issue_rejects_missing_identity() {
        AuthUserContext user = AuthUserContext.builder()
                .username(null)
                .displayName("Missing Identity")
                .email(null)
                .role("VIEWER")
                .build();

        assertThrows(IllegalStateException.class, () -> service.issueToken(user, 60));
    }
}
