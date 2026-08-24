package com.sdd.platform.application.usecase.governance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.config.AppProperties;
import com.sdd.platform.domain.model.AuthUserContext;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthTokenService {

    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectMapper objectMapper;
    private final byte[] signingKey;

    public AuthTokenService(AppProperties appProperties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.signingKey = decodeSecret(appProperties.jwt().secret());
    }

    public String issueToken(AuthUserContext user, int expiresInSeconds) {
        long now = Instant.now().getEpochSecond();
        long exp = now + expiresInSeconds;
        UUID userId = user.getUserAccountId();
        if (userId == null && user.getUsername() != null) {
            userId = UUID.nameUUIDFromBytes(user.getUsername().getBytes(StandardCharsets.UTF_8));
        }
        if (userId == null) {
            throw new IllegalStateException("Auth user identity is missing");
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("sub", user.getUsername());
        payload.put("uid", userId.toString());
        payload.put("displayName", user.getDisplayName());
        payload.put("email", user.getEmail());
        payload.put("role", user.getRole());
        payload.put("accessScopes", user.getAccessScopes());
        payload.put("iat", now);
        payload.put("exp", exp);
        try {
            String encodedHeader = base64Url(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
            String encodedPayload = base64Url(objectMapper.writeValueAsBytes(payload));
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = base64Url(hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8)));
            return signingInput + "." + signature;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode auth token", e);
        }
    }

    public AuthUserContext parseAndValidate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }

        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8));
        byte[] actual;
        try {
            actual = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        if (!java.security.MessageDigest.isEqual(expected, actual)) {
            return null;
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
        } catch (Exception ex) {
            return null;
        }

        long exp = payload.path("exp").asLong(0L);
        if (exp <= Instant.now().getEpochSecond()) {
            return null;
        }

        String username = payload.path("sub").asText(null);
        String uid = payload.path("uid").asText(null);
        String displayName = payload.path("displayName").asText(null);
        String email = payload.hasNonNull("email") ? payload.path("email").asText(null) : null;
        String roleValue = payload.path("role").asText(null);
        if (username == null || uid == null || roleValue == null) {
            return null;
        }

        List<String> accessScopes = new ArrayList<>();
        JsonNode scopes = payload.path("accessScopes");
        if (scopes.isArray()) {
            scopes.forEach(node -> {
                if (!node.isNull()) {
                    accessScopes.add(node.asText());
                }
            });
        }

        try {
            return AuthUserContext.builder()
                    .userAccountId(UUID.fromString(uid))
                    .username(username)
                    .displayName(displayName)
                    .email(email)
                    .role(roleValue)
                    .accessScopes(accessScopes)
                    .build();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public String issueRefreshToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return base64Url(bytes);
    }

    public String hashToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token is required");
        }
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash auth token", ex);
        }
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private byte[] hmacSha256(byte[] input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return mac.doFinal(input);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign auth token", ex);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] decodeSecret(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
