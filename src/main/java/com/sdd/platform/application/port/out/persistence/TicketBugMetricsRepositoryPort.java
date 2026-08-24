package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.domain.model.TicketBugMetricsModel;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketBugMetricsRepositoryPort {

    Optional<TicketBugMetricsModel> findById(UUID ticketBugId);

    List<TicketBugMetricsModel> findPage(UUID projectId, UUID repositoryId, String search, int offset, int limit);

    long count(UUID projectId, UUID repositoryId, String search);

    boolean existsActiveByTicketId(UUID ticketId);

    Optional<TicketBugMetricsModel> findActiveByTicketId(UUID ticketId);

    TicketBugMetricsModel insert(TicketBugMetricsModel entity);

    int update(TicketBugMetricsModel entity);

    int softDelete(
            UUID ticketBugId,
            String deletedBy,
            OffsetDateTime deletedAt,
            String updatedBy,
            OffsetDateTime updatedAt
    );
}
