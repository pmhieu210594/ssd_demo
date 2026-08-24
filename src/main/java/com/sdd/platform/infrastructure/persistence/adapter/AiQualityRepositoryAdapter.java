package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.AiQualityRepositoryPort;
import com.sdd.platform.domain.model.AiQualityModel;
import com.sdd.platform.infrastructure.persistence.mapper.AiQualityMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AiQualityRepositoryAdapter implements AiQualityRepositoryPort {

    private final AiQualityMapper mapper;

    public AiQualityRepositoryAdapter(AiQualityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AiQualityModel> findById(UUID ticketAiQualityId) {
        return mapper.findById(ticketAiQualityId);
    }

    @Override
    public List<AiQualityModel> findPage(UUID projectId, UUID repositoryId, UUID ticketId, String search, int offset, int limit) {
        return mapper.findPage(projectId, repositoryId, ticketId, search, offset, limit);
    }

    @Override
    public long count(UUID projectId, UUID repositoryId, UUID ticketId, String search) {
        return mapper.count(projectId, repositoryId, ticketId, search);
    }

    @Override
    public boolean existsActiveByTicketId(UUID ticketId) {
        return mapper.existsActiveByTicketId(ticketId);
    }

    @Override
    public Optional<AiQualityModel> findActiveByTicketId(UUID ticketId) {
        return mapper.findActiveByTicketId(ticketId);
    }

    @Override
    public AiQualityModel insert(AiQualityModel entity) {
        mapper.insert(entity);
        return entity;
    }

    @Override
    public int update(AiQualityModel entity) {
        return mapper.update(entity);
    }

    @Override
    public int softDelete(UUID ticketAiQualityId, String deletedBy, OffsetDateTime deletedAt, String updatedBy, OffsetDateTime updatedAt) {
        return mapper.softDelete(ticketAiQualityId, deletedBy, deletedAt, updatedBy, updatedAt);
    }
}
