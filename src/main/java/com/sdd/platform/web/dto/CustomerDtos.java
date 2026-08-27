package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.domain.model.Customer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CustomerDtos {
    private CustomerDtos() {}

    public record CustomerDto(
            UUID customerId,
            UUID organizationId,
            String organizationName,
            String customerCode,
            String customerAlias,
            String classification,
            String status,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy,
            OffsetDateTime deletedAt,
            String deletedBy,
            long version
    ) {
        public static CustomerDto from(Customer customer) {
            return new CustomerDto(
                    customer.getCustomerId(),
                    customer.getOrganizationId(),
                    customer.getOrganizationName(),
                    customer.getCustomerCode(),
                    customer.getCustomerAlias(),
                    customer.getClassification() == null ? null : customer.getClassification().name(),
                    customer.getStatus() == null ? null : customer.getStatus().name(),
                    customer.getCreatedAt(),
                    customer.getCreatedBy(),
                    customer.getUpdatedAt(),
                    customer.getUpdatedBy(),
                    customer.getDeletedAt(),
                    customer.getDeletedBy(),
                    customer.getVersion()
            );
        }
    }

    public record CustomerPageDto(
            List<CustomerDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static CustomerPageDto from(PageResult<Customer> result) {
            return new CustomerPageDto(
                    result.items().stream().map(CustomerDto::from).toList(),
                    result.page(),
                    result.size(),
                    result.totalElements(),
                    result.totalPages()
            );
        }
    }

    public record CreateCustomerRequest(
            UUID organizationId,
            String customerCode,
            String customerAlias,
            String classification
    ) {
    }

    public record UpdateCustomerRequest(
            UUID organizationId,
            String customerCode,
            String customerAlias,
            String classification,
            long version
    ) {
    }

    public record DeleteCustomerRequest(long version) {
    }
}
