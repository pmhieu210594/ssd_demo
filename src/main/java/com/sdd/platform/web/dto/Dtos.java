package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.governance.AuthLoginResult;
import com.sdd.platform.domain.model.*;
import com.sdd.platform.domain.model.AuthUserContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

/**
 * Response DTOs for the slim demo and LOGIN MVP.
 */
public final class Dtos {
    private Dtos() {}

    public record HealthDto(String status, String traceId) {}

    public record UserDto(Long id, String email, String displayName, String role, String avatarUrl) {
        public static UserDto from(AppUser u) {
            return new UserDto(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole().name(), u.getAvatarUrl());
        }
    }

    public record AuthCurrentUserDto(
            String username,
            String displayName,
            String email,
            String role,
            List<String> accessScopes
    ) {
        public static AuthCurrentUserDto from(AuthUserContext user) {
            return new AuthCurrentUserDto(
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getEmail(),
                    user.getRole(),
                    user.getAccessScopes()
            );
        }
    }

    public record AuthLoginRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(max = 256) String password
    ) {}

    public record AuthLoginResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            int expiresInSeconds,
            AuthCurrentUserDto user,
            String redirectTo
    ) {
        public static AuthLoginResponse from(AuthLoginResult result) {
            return new AuthLoginResponse(
                    result.accessToken(),
                    result.refreshToken(),
                    result.tokenType(),
                    result.expiresInSeconds(),
                    AuthCurrentUserDto.from(result.user()),
                    result.redirectTo()
            );
        }
    }

    public record ConnectorRunDto(
            UUID id, String connectorName, String status,
            int recordsIngested, String errorMessage,
            OffsetDateTime startedAt, OffsetDateTime finishedAt
    ) {
        public static ConnectorRunDto from(ConnectorRun r) {
            return new ConnectorRunDto(r.getId(), r.getConnectorName(), r.getStatus().name(),
                    r.getRecordsIngested(), r.getErrorMessage(),
                    r.getStartedAt(), r.getFinishedAt());
        }
    }
}
