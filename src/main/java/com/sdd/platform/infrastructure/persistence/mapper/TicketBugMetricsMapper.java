package com.sdd.platform.infrastructure.persistence.mapper;

import com.sdd.platform.domain.model.TicketBugMetricsModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface TicketBugMetricsMapper {

    Optional<TicketBugMetricsModel> findById(@Param("ticketBugId") UUID ticketBugId);

    List<TicketBugMetricsModel> findPage(
            @Param("projectId") UUID projectId,
            @Param("repositoryId") UUID repositoryId,
            @Param("search") String search,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long count(
            @Param("projectId") UUID projectId,
            @Param("repositoryId") UUID repositoryId,
            @Param("search") String search
    );

    boolean existsActiveByTicketId(@Param("ticketId") UUID ticketId);

    Optional<TicketBugMetricsModel> findActiveByTicketId(@Param("ticketId") UUID ticketId);

    void insert(TicketBugMetricsModel entity);

    int update(TicketBugMetricsModel entity);

    int softDelete(
            @Param("ticketBugId") UUID ticketBugId,
            @Param("deletedBy") String deletedBy,
            @Param("deletedAt") OffsetDateTime deletedAt,
            @Param("updatedBy") String updatedBy,
            @Param("updatedAt") OffsetDateTime updatedAt
    );
}
