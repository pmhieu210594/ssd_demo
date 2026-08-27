package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.governance.CustomerService;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.web.dto.CustomerDtos;
import com.sdd.platform.web.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

        private final CustomerService service;

        public CustomerController(CustomerService service) {
                this.service = service;
        }

        @GetMapping
        public CustomerDtos.CustomerPageDto list(
                        @RequestParam(required = false) UUID organizationId,
                        @RequestParam(required = false) String keyword,
                        @RequestParam(required = false) String classification,
                        @RequestParam(required = false) String status,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int pageSize,
                        @CurrentUser AppUser caller) {
                return CustomerDtos.CustomerPageDto.from(
                                service.search(organizationId, keyword, classification, status, page, pageSize,
                                                caller));
        }

        @GetMapping("/{customerId}")
        public CustomerDtos.CustomerDto get(@PathVariable UUID customerId, @CurrentUser AppUser caller) {
                return CustomerDtos.CustomerDto.from(service.get(customerId, caller));
        }

        @PostMapping
        public ResponseEntity<CustomerDtos.CustomerDto> create(
                        @RequestBody CustomerDtos.CreateCustomerRequest request,
                        @CurrentUser AppUser caller) {
                return ResponseEntity.status(HttpStatus.OK).body(
                                CustomerDtos.CustomerDto.from(service.create(
                                                request.organizationId(),
                                                request.customerCode(),
                                                request.customerAlias(),
                                                request.classification(),
                                                caller)));
        }

        @PutMapping("/{customerId}")
        public CustomerDtos.CustomerDto update(
                        @PathVariable UUID customerId,
                        @RequestBody CustomerDtos.UpdateCustomerRequest request,
                        @CurrentUser AppUser caller) {
                return CustomerDtos.CustomerDto.from(service.update(
                                customerId,
                                request.organizationId(),
                                request.customerCode(),
                                request.customerAlias(),
                                request.classification(),
                                request.version(),
                                caller));
        }

        @PatchMapping("/{customerId}/delete")
        public CustomerDtos.CustomerDto softDelete(
                        @PathVariable UUID customerId,
                        @RequestBody CustomerDtos.DeleteCustomerRequest request,
                        @CurrentUser AppUser caller) {
                return CustomerDtos.CustomerDto.from(service.softDelete(customerId, request.version(), caller));
        }
}
