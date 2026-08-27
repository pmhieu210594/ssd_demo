package com.sdd.platform.application.port.out.persistence;

import java.util.List;
import java.util.UUID;

public interface AcCoveragePort {

    /**
     * Returns ac_key values of all ACTIVE acceptance criteria for the given ticket,
     * sourced from tbl_fact_acceptance_criteria.
     * Returns an empty list if the spec-pack has not been parsed yet for this ticket.
     */
    List<String> findActiveAcKeys(UUID ticketId);
}
