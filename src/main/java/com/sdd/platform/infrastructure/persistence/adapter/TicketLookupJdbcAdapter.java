package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.TicketLookupPort;
import com.sdd.platform.domain.model.TicketOption;
import com.sdd.platform.infrastructure.persistence.mapper.TicketLookupMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class TicketLookupJdbcAdapter implements TicketLookupPort {

    private final TicketLookupMapper mapper;

    public TicketLookupJdbcAdapter(TicketLookupMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean existsTicketInProject(UUID ticketId, UUID projectId) {
        return mapper.existsTicketInProject(ticketId, projectId);
    }

    @Override
    public boolean existsTicketInRepository(UUID ticketId, UUID repositoryId) {
        return mapper.existsTicketInRepository(ticketId, repositoryId);
    }

    @Override
    public List<TicketOption> findOptionsByProject(UUID projectId, UUID repositoryId) {
        return mapper.findOptionsByProject(projectId, repositoryId);
    }
}
