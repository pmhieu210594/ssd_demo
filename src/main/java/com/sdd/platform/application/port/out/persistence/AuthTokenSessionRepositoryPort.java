package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.domain.model.AuthTokenSession;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface AuthTokenSessionRepositoryPort {

    void save(AuthTokenSession session);

    Optional<AuthTokenSession> findActiveByAccessTokenHash(String accessTokenHash, OffsetDateTime now);

    void revokeByUsername(String username, OffsetDateTime revokedAt);
}
