package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.CustomerRepositoryPort;
import com.sdd.platform.domain.model.Customer;
import com.sdd.platform.infrastructure.persistence.mapper.CustomerMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CustomerRepositoryAdapter implements CustomerRepositoryPort {

    private final CustomerMapper mapper;

    public CustomerRepositoryAdapter(CustomerMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Customer> findById(UUID customerId) {
        return mapper.findById(customerId);
    }

    @Override
    public List<Customer> findPage(UUID organizationId, String keyword, String classification, String status, int offset, int limit) {
        return mapper.findPage(organizationId, keyword, classification, status, offset, limit);
    }

    @Override
    public long count(UUID organizationId, String keyword, String classification, String status) {
        return mapper.count(organizationId, keyword, classification, status);
    }

    @Override
    public boolean existsActiveCode(String customerCode, UUID excludeCustomerId) {
        return mapper.existsActiveCode(customerCode, excludeCustomerId);
    }

    @Override
    public boolean existsActiveAlias(UUID organizationId, String customerAlias, UUID excludeCustomerId) {
        return mapper.existsActiveAlias(organizationId, customerAlias, excludeCustomerId);
    }

    @Override
    public Customer insert(Customer customer) {
        mapper.insert(customer);
        return customer;
    }

    @Override
    public int update(Customer customer) {
        return mapper.update(customer);
    }

    @Override
    public int softDelete(UUID customerId, long version, String deletedBy, OffsetDateTime deletedAt, String updatedBy, OffsetDateTime updatedAt) {
        return mapper.softDelete(customerId, version, deletedBy, deletedAt, updatedBy, updatedAt);
    }
}
