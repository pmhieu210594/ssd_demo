package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.domain.model.Customer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepositoryPort {

    Optional<Customer> findById(UUID customerId);

    List<Customer> findPage(
            UUID organizationId,
            String keyword,
            String classification,
            String status,
            int offset,
            int limit
    );

    long count(
            UUID organizationId,
            String keyword,
            String classification,
            String status
    );

    boolean existsActiveCode(String customerCode, UUID excludeCustomerId);

    boolean existsActiveAlias(UUID organizationId, String customerAlias, UUID excludeCustomerId);

    Customer insert(Customer customer);

    int update(Customer customer);

    int softDelete(
            UUID customerId,
            long version,
            String deletedBy,
            OffsetDateTime deletedAt,
            String updatedBy,
            OffsetDateTime updatedAt
    );
}
