package com.sdd.platform.infrastructure.persistence.mapper;

import com.sdd.platform.domain.model.AuthTokenSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

@Mapper
public interface AuthTokenSessionMapper {

    void insert(AuthTokenSession session);

    Optional<AuthTokenSession> findActiveByAccessTokenHash(
            @Param("accessTokenHash") String accessTokenHash,
            @Param("now") OffsetDateTime now
    );

    void revokeByUsername(@Param("username") String username, @Param("revokedAt") OffsetDateTime revokedAt);
}
