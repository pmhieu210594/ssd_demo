package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.TicketBugMetricsRepositoryPort;
import com.sdd.platform.domain.model.TicketBugMetricsModel;
import com.sdd.platform.infrastructure.persistence.mapper.TicketBugMetricsMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TicketBugMetricsRepositoryAdapter implements TicketBugMetricsRepositoryPort {

    private final TicketBugMetricsMapper mapper;

    public TicketBugMetricsRepositoryAdapter(TicketBugMetricsMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<TicketBugMetricsModel> findById(UUID ticketBugId) {
        return mapper.findById(ticketBugId);
    }

    @Override
    public List<TicketBugMetricsModel> findPage(UUID projectId, UUID repositoryId, String search, int offset, int limit) {
        return mapper.findPage(projectId, repositoryId, search, offset, limit);
    }

    @Override
    public long count(UUID projectId, UUID repositoryId, String search) {
        return mapper.count(projectId, repositoryId, search);
    }

    @Override
    public boolean existsActiveByTicketId(UUID ticketId) {
        return mapper.existsActiveByTicketId(ticketId);
    }

    @Override
    public Optional<TicketBugMetricsModel> findActiveByTicketId(UUID ticketId) {
        return mapper.findActiveByTicketId(ticketId);
    }

    @Override
    public TicketBugMetricsModel insert(TicketBugMetricsModel entity) {
        mapper.insert(entity);
        return entity;
    }

    @Override
    public int update(TicketBugMetricsModel entity) {
        return mapper.update(entity);
    }

    @Override
    public int softDelete(UUID ticketBugId, String deletedBy, OffsetDateTime deletedAt, String updatedBy, OffsetDateTime updatedAt) {
        return mapper.softDelete(ticketBugId, deletedBy, deletedAt, updatedBy, updatedAt);
    }
}
