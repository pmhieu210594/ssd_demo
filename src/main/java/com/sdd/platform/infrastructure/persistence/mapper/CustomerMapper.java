package com.sdd.platform.infrastructure.persistence.mapper;

import com.sdd.platform.domain.model.Customer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface CustomerMapper {

    Optional<Customer> findById(@Param("customerId") UUID customerId);

    List<Customer> findPage(
            @Param("organizationId") UUID organizationId,
            @Param("keyword") String keyword,
            @Param("classification") String classification,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long count(
            @Param("organizationId") UUID organizationId,
            @Param("keyword") String keyword,
            @Param("classification") String classification,
            @Param("status") String status
    );

    boolean existsActiveCode(
            @Param("customerCode") String customerCode,
            @Param("excludeCustomerId") UUID excludeCustomerId
    );

    boolean existsActiveAlias(
            @Param("organizationId") UUID organizationId,
            @Param("customerAlias") String customerAlias,
            @Param("excludeCustomerId") UUID excludeCustomerId
    );

    void insert(Customer customer);

    int update(Customer customer);

    int softDelete(
            @Param("customerId") UUID customerId,
            @Param("version") long version,
            @Param("deletedBy") String deletedBy,
            @Param("deletedAt") OffsetDateTime deletedAt,
            @Param("updatedBy") String updatedBy,
            @Param("updatedAt") OffsetDateTime updatedAt
    );
}
