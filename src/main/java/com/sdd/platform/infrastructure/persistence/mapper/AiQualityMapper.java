package com.sdd.platform.infrastructure.persistence.mapper;

import com.sdd.platform.domain.model.AiQualityModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface AiQualityMapper {

    Optional<AiQualityModel> findById(@Param("ticketAiQualityId") UUID ticketAiQualityId);

    List<AiQualityModel> findPage(
            @Param("projectId") UUID projectId,
            @Param("repositoryId") UUID repositoryId,
            @Param("ticketId") UUID ticketId,
            @Param("search") String search,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long count(
            @Param("projectId") UUID projectId,
            @Param("repositoryId") UUID repositoryId,
            @Param("ticketId") UUID ticketId,
            @Param("search") String search
    );

    boolean existsActiveByTicketId(@Param("ticketId") UUID ticketId);

    Optional<AiQualityModel> findActiveByTicketId(@Param("ticketId") UUID ticketId);

    void insert(AiQualityModel entity);

    int update(AiQualityModel entity);

    int softDelete(
            @Param("ticketAiQualityId") UUID ticketAiQualityId,
            @Param("deletedBy") String deletedBy,
            @Param("deletedAt") OffsetDateTime deletedAt,
            @Param("updatedBy") String updatedBy,
            @Param("updatedAt") OffsetDateTime updatedAt
    );
}
