package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.AuthTokenSessionRepositoryPort;
import com.sdd.platform.domain.model.AuthTokenSession;
import com.sdd.platform.infrastructure.persistence.mapper.AuthTokenSessionMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class AuthTokenSessionRepositoryAdapter implements AuthTokenSessionRepositoryPort {

    private final AuthTokenSessionMapper mapper;

    public AuthTokenSessionRepositoryAdapter(AuthTokenSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(AuthTokenSession session) {
        mapper.insert(session);
    }

    @Override
    public Optional<AuthTokenSession> findActiveByAccessTokenHash(String accessTokenHash, OffsetDateTime now) {
        return mapper.findActiveByAccessTokenHash(accessTokenHash, now);
    }

    @Override
    public void revokeByUsername(String username, OffsetDateTime revokedAt) {
        mapper.revokeByUsername(username, revokedAt);
    }
}
