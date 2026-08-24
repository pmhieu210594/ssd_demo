package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.domain.model.AiQualityModel;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiQualityRepositoryPort {

    Optional<AiQualityModel> findById(UUID ticketAiQualityId);

    List<AiQualityModel> findPage(UUID projectId, UUID repositoryId, UUID ticketId, String search, int offset, int limit);

    long count(UUID projectId, UUID repositoryId, UUID ticketId, String search);

    boolean existsActiveByTicketId(UUID ticketId);

    Optional<AiQualityModel> findActiveByTicketId(UUID ticketId);

    AiQualityModel insert(AiQualityModel entity);

    int update(AiQualityModel entity);

    int softDelete(
            UUID ticketAiQualityId,
            String deletedBy,
            OffsetDateTime deletedAt,
            String updatedBy,
            OffsetDateTime updatedAt
    );
}
