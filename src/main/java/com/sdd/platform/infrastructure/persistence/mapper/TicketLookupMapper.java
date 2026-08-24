package com.sdd.platform.infrastructure.persistence.mapper;

import com.sdd.platform.domain.model.TicketOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface TicketLookupMapper {

    boolean existsTicketInProject(@Param("ticketId") UUID ticketId, @Param("projectId") UUID projectId);

    boolean existsTicketInRepository(@Param("ticketId") UUID ticketId, @Param("repositoryId") UUID repositoryId);

    List<TicketOption> findOptionsByProject(@Param("projectId") UUID projectId, @Param("repositoryId") UUID repositoryId);
}
