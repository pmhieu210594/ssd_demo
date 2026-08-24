package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.domain.model.TicketOption;

import java.util.List;
import java.util.UUID;

/**
 * Minimal read-only lookup over tbl_dim_ticket, scoped to what
 * TicketBugMetrics needs: existence checks and option lists for the FE
 * cascading filter. tbl_dim_ticket has no repository_id column, so when
 * repositoryId is provided the match is resolved indirectly through the
 * ticket's linked pull requests / commits in that repository.
 */
public interface TicketLookupPort {

    boolean existsTicketInProject(UUID ticketId, UUID projectId);

    boolean existsTicketInRepository(UUID ticketId, UUID repositoryId);

    List<TicketOption> findOptionsByProject(UUID projectId, UUID repositoryId);
}
