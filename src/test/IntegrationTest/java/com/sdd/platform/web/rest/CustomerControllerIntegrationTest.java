package com.sdd.platform.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.exception.OptimisticLockingException;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.application.usecase.governance.CustomerService;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.Customer;
import com.sdd.platform.web.exception.GlobalExceptionHandler;
import com.sdd.platform.web.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerControllerIntegrationTest {

        private MockMvc mockMvc;
        private ObjectMapper objectMapper;
        private CustomerService service;
        private AppUser caller;

        @BeforeEach
        void setUp() {
                service = Mockito.mock(CustomerService.class);
                objectMapper = new ObjectMapper().findAndRegisterModules();
                caller = AppUser.builder()
                                .id(1L)
                                .provider("google")
                                .providerUid("phase6-admin")
                                .email("phase6-admin@example.com")
                                .displayName("Phase 6 Admin")
                                .role(AppUser.Role.ADMIN)
                                .active(true)
                                .build();

                mockMvc = MockMvcBuilders
                                .standaloneSetup(new CustomerController(service))
                                .setControllerAdvice(new GlobalExceptionHandler())
                                .setCustomArgumentResolvers(new Phase6CurrentUserArgumentResolver(caller))
                                .build();
        }

        @Test
        void list_returnsPagedCustomersAndPassesSearchParameters() throws Exception {
                UUID organizationId = UUID.randomUUID();
                Customer customer = customer(UUID.randomUUID(), organizationId, "Acme Vietnam", "CUS-001", "Acme VN",
                                Customer.CustomerClassification.EXTERNAL, Customer.CustomerStatus.ACTIVE, 0L);
                when(service.search(eq(organizationId), eq("Acme"), eq("EXTERNAL"), eq("ACTIVE"), eq(1), eq(10),
                                eq(caller)))
                                .thenReturn(new PageResult<>(List.of(customer), 1, 10, 11, 2));

                mockMvc.perform(get("/api/v1/customers")
                                .param("organizationId", organizationId.toString())
                                .param("keyword", "Acme")
                                .param("classification", "EXTERNAL")
                                .param("status", "ACTIVE")
                                .param("page", "1")
                                .param("pageSize", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items[0].customerCode").value("CUS-001"))
                                .andExpect(jsonPath("$.items[0].customerAlias").value("Acme VN"))
                                .andExpect(jsonPath("$.items[0].organizationId").value(organizationId.toString()))
                                .andExpect(jsonPath("$.items[0].organizationName").value("Acme Vietnam"))
                                .andExpect(jsonPath("$.items[0].classification").value("EXTERNAL"))
                                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                                .andExpect(jsonPath("$.page").value(1))
                                .andExpect(jsonPath("$.size").value(10))
                                .andExpect(jsonPath("$.totalElements").value(11))
                                .andExpect(jsonPath("$.totalPages").value(2));

                verify(service).search(eq(organizationId), eq("Acme"), eq("EXTERNAL"), eq("ACTIVE"), eq(1), eq(10),
                                eq(caller));
        }

        @Test
        void list_usesDefaultActiveFilterWhenStatusIsOmitted() throws Exception {
                when(service.search(nullable(UUID.class), nullable(String.class), nullable(String.class), nullable(String.class),
                                eq(0), eq(20), eq(caller)))
                                .thenReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

                mockMvc.perform(get("/api/v1/customers"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items").isArray())
                                .andExpect(jsonPath("$.page").value(0))
                                .andExpect(jsonPath("$.size").value(20));

                verify(service).search(nullable(UUID.class), nullable(String.class), nullable(String.class), nullable(String.class),
                                eq(0), eq(20), eq(caller));
        }

        @Test
        void get_returnsCustomerDetail() throws Exception {
                UUID customerId = UUID.randomUUID();
                UUID organizationId = UUID.randomUUID();
                when(service.get(eq(customerId), eq(caller)))
                                .thenReturn(customer(customerId, organizationId, "Detail Org", "CUS-DETAIL", "Detail Alias",
                                                Customer.CustomerClassification.INTERNAL, Customer.CustomerStatus.ACTIVE, 7L));

                mockMvc.perform(get("/api/v1/customers/{customerId}", customerId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                                .andExpect(jsonPath("$.organizationId").value(organizationId.toString()))
                                .andExpect(jsonPath("$.organizationName").value("Detail Org"))
                                .andExpect(jsonPath("$.customerCode").value("CUS-DETAIL"))
                                .andExpect(jsonPath("$.customerAlias").value("Detail Alias"))
                                .andExpect(jsonPath("$.classification").value("INTERNAL"))
                                .andExpect(jsonPath("$.status").value("ACTIVE"))
                                .andExpect(jsonPath("$.version").value(7));
        }

        @Test
        void get_returns404WhenMissing() throws Exception {
                UUID customerId = UUID.randomUUID();
                when(service.get(eq(customerId), eq(caller))).thenThrow(new NotFoundException("Pages.Customer.NotFound"));

                mockMvc.perform(get("/api/v1/customers/{customerId}", customerId))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                                .andExpect(jsonPath("$.message").value("Pages.Customer.NotFound"));
        }

        @Test
        void create_returns200AndCustomerBody() throws Exception {
                UUID customerId = UUID.randomUUID();
                UUID organizationId = UUID.randomUUID();
                when(service.create(eq(organizationId), eq("CUS-002"), eq("Beta Alias"), eq("EXTERNAL"), eq(caller)))
                                .thenReturn(customer(customerId, organizationId, "Beta Org", "CUS-002", "Beta Alias",
                                                Customer.CustomerClassification.EXTERNAL, Customer.CustomerStatus.ACTIVE, 0L));

                mockMvc.perform(post("/api/v1/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "organizationId", organizationId,
                                                "customerCode", "CUS-002",
                                                "customerAlias", "Beta Alias",
                                                "classification", "EXTERNAL"))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                                .andExpect(jsonPath("$.organizationId").value(organizationId.toString()))
                                .andExpect(jsonPath("$.customerCode").value("CUS-002"))
                                .andExpect(jsonPath("$.customerAlias").value("Beta Alias"))
                                .andExpect(jsonPath("$.classification").value("EXTERNAL"))
                                .andExpect(jsonPath("$.status").value("ACTIVE"))
                                .andExpect(jsonPath("$.version").value(0));
        }

        @Test
        void create_returns400WhenOrganizationUnavailable() throws Exception {
                UUID organizationId = UUID.randomUUID();
                when(service.create(eq(organizationId), eq("CUS-003"), eq("Unavailable Org"), eq("INTERNAL"), eq(caller)))
                                .thenThrow(new BusinessRuleException("Pages.Customer.Organization.Unavailable"));

                mockMvc.perform(post("/api/v1/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "organizationId", organizationId,
                                                "customerCode", "CUS-003",
                                                "customerAlias", "Unavailable Org",
                                                "classification", "INTERNAL"))))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").value("DOMAIN_RULE_VIOLATION"))
                                .andExpect(jsonPath("$.message").value("Pages.Customer.Organization.Unavailable"));
        }

        @Test
        void create_returns400WhenDuplicateCode() throws Exception {
                UUID organizationId = UUID.randomUUID();
                when(service.create(eq(organizationId), eq("CUS-DUP"), eq("Duplicate Code"), eq("EXTERNAL"), eq(caller)))
                                .thenThrow(new BusinessRuleException("Pages.Customer.Code.Duplicate"));

                mockMvc.perform(post("/api/v1/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "organizationId", organizationId,
                                                "customerCode", "CUS-DUP",
                                                "customerAlias", "Duplicate Code",
                                                "classification", "EXTERNAL"))))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value("Pages.Customer.Code.Duplicate"));
        }

        @Test
        void create_returns400WhenDuplicateAliasWithinOrganization() throws Exception {
                UUID organizationId = UUID.randomUUID();
                when(service.create(eq(organizationId), eq("CUS-ALIAS"), eq("Duplicate Alias"), eq("INTERNAL"), eq(caller)))
                                .thenThrow(new BusinessRuleException("Pages.Customer.Alias.Duplicate"));

                mockMvc.perform(post("/api/v1/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "organizationId", organizationId,
                                                "customerCode", "CUS-ALIAS",
                                                "customerAlias", "Duplicate Alias",
                                                "classification", "INTERNAL"))))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value("Pages.Customer.Alias.Duplicate"));
        }

        @Test
        void create_returns400WhenRequiredFieldMessageKeyIsReturned() throws Exception {
                UUID organizationId = UUID.randomUUID();
                when(service.create(eq(organizationId), eq(""), eq("Alias"), eq("INTERNAL"), eq(caller)))
                                .thenThrow(new BusinessRuleException("Pages.Customer.Code.Required"));

                mockMvc.perform(post("/api/v1/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "organizationId", organizationId,
                                                "customerCode", "",
                                                "customerAlias", "Alias",
                                                "classification", "INTERNAL"))))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").value("DOMAIN_RULE_VIOLATION"))
                                .andExpect(jsonPath("$.message").value("Pages.Customer.Code.Required"));
        }

        @Test
        void update_returnsUpdatedCustomerBody() throws Exception {
                UUID customerId = UUID.randomUUID();
                UUID organizationId = UUID.randomUUID();
                when(service.update(eq(customerId), eq(organizationId), eq("CUS-004"), eq("Gamma Alias"),
                                eq("INTERNAL"), eq(1L), eq(caller)))
                                .thenReturn(customer(customerId, organizationId, "Gamma Org", "CUS-004", "Gamma Alias",
                                                Customer.CustomerClassification.INTERNAL, Customer.CustomerStatus.ACTIVE, 2L));

                mockMvc.perform(put("/api/v1/customers/{customerId}", customerId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "organizationId", organizationId,
                                                "customerCode", "CUS-004",
                                                "customerAlias", "Gamma Alias",
                                                "classification", "INTERNAL",
                                                "version", 1L))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.organizationId").value(organizationId.toString()))
                                .andExpect(jsonPath("$.customerCode").value("CUS-004"))
                                .andExpect(jsonPath("$.customerAlias").value("Gamma Alias"))
                                .andExpect(jsonPath("$.classification").value("INTERNAL"))
                                .andExpect(jsonPath("$.version").value(2));
        }

        @Test
        void update_returns400WhenDeletedCustomerCannotBeEdited() throws Exception {
                UUID customerId = UUID.randomUUID();
                UUID organizationId = UUID.randomUUID();
                when(service.update(eq(customerId), eq(organizationId), eq("CUS-004"), eq("Deleted Alias"),
                                eq("INTERNAL"), eq(1L), eq(caller)))
                                .thenThrow(new BusinessRuleException("Pages.Customer.Deleted.CannotEdit"));

                mockMvc.perform(put("/api/v1/customers/{customerId}", customerId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "organizationId", organizationId,
                                                "customerCode", "CUS-004",
                                                "customerAlias", "Deleted Alias",
                                                "classification", "INTERNAL",
                                                "version", 1L))))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value("Pages.Customer.Deleted.CannotEdit"));
        }

        @Test
        void update_returns409WhenVersionIsStale() throws Exception {
                UUID customerId = UUID.randomUUID();
                UUID organizationId = UUID.randomUUID();
                when(service.update(eq(customerId), eq(organizationId), eq("CUS-004"), eq("Gamma Alias"),
                                eq("INTERNAL"), eq(1L), eq(caller)))
                                .thenThrow(new OptimisticLockingException("Pages.Customer.Conflict.Version"));

                mockMvc.perform(put("/api/v1/customers/{customerId}", customerId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "organizationId", organizationId,
                                                "customerCode", "CUS-004",
                                                "customerAlias", "Gamma Alias",
                                                "classification", "INTERNAL",
                                                "version", 1L))))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.error").value("CONFLICT"))
                                .andExpect(jsonPath("$.message").value("Pages.Customer.Conflict.Version"));
        }

        @Test
        void softDelete_callsDeleteEndpointWithVersion() throws Exception {
                UUID customerId = UUID.randomUUID();
                UUID organizationId = UUID.randomUUID();
                when(service.softDelete(eq(customerId), eq(4L), eq(caller)))
                                .thenReturn(customer(customerId, organizationId, "Delta Org", "CUS-005", "Delta Alias",
                                                Customer.CustomerClassification.EXTERNAL, Customer.CustomerStatus.DELETED, 5L));

                mockMvc.perform(patch("/api/v1/customers/{customerId}/delete", customerId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("version", 4L))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.customerCode").value("CUS-005"))
                                .andExpect(jsonPath("$.status").value("DELETED"))
                                .andExpect(jsonPath("$.deletedAt").exists())
                                .andExpect(jsonPath("$.version").value(5));
        }

        @Test
        void softDelete_returns409WhenVersionIsStale() throws Exception {
                UUID customerId = UUID.randomUUID();
                when(service.softDelete(eq(customerId), eq(4L), eq(caller)))
                                .thenThrow(new OptimisticLockingException("Pages.Customer.Conflict.Version"));

                mockMvc.perform(patch("/api/v1/customers/{customerId}/delete", customerId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("version", 4L))))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.message").value("Pages.Customer.Conflict.Version"));
        }

        @Test
        void directCustomerApiReturns403WhenServiceRejectsNonAdmin() throws Exception {
                when(service.search(nullable(UUID.class), nullable(String.class), nullable(String.class), nullable(String.class),
                                eq(0), eq(20), eq(caller)))
                                .thenThrow(new ForbiddenException("Pages.Customer.Error.Forbidden"));

                mockMvc.perform(get("/api/v1/customers"))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                                .andExpect(jsonPath("$.message").value("Pages.Customer.Error.Forbidden"));
        }

        private Customer customer(UUID customerId, UUID organizationId, String organizationName, String code, String alias,
                        Customer.CustomerClassification classification, Customer.CustomerStatus status, long version) {
                OffsetDateTime now = OffsetDateTime.parse("2026-06-10T00:00:00Z");
                return Customer.builder()
                                .customerId(customerId)
                                .organizationId(organizationId)
                                .organizationName(organizationName)
                                .customerCode(code)
                                .customerAlias(alias)
                                .classification(classification)
                                .status(status)
                                .createdAt(now)
                                .createdBy("tester")
                                .updatedAt(now)
                                .updatedBy("tester")
                                .deletedAt(status == Customer.CustomerStatus.DELETED ? now : null)
                                .deletedBy(status == Customer.CustomerStatus.DELETED ? "tester" : null)
                                .version(version)
                                .build();
        }

        private static class Phase6CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {
                private final AppUser caller;

                private Phase6CurrentUserArgumentResolver(AppUser caller) {
                        this.caller = caller;
                }

                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType().equals(AppUser.class)
                                        && parameter.hasParameterAnnotation(CurrentUser.class);
                }

                @Override
                public Object resolveArgument(
                                MethodParameter parameter,
                                ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest,
                                WebDataBinderFactory binderFactory) {
                        return caller;
                }
        }
}
