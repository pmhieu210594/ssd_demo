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
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerControllerTest {

    private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private CustomerService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(CustomerService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedCurrentUserResolver(adminUser()))
                .build();
    }

    @Test
    void list_returnsPagedCustomersAndPassesFiltersToService() throws Exception {
        when(service.search(eq(ORG_ID), eq("Brycen"), eq("INTERNAL"), eq("ACTIVE"), eq(2), eq(25), any(AppUser.class)))
                .thenReturn(new PageResult<>(List.of(activeCustomer()), 2, 25, 51L, 3));

        mockMvc.perform(get("/api/v1/customers")
                        .param("organizationId", ORG_ID.toString())
                        .param("keyword", "Brycen")
                        .param("classification", "INTERNAL")
                        .param("status", "ACTIVE")
                        .param("page", "2")
                        .param("pageSize", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].customerId").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.items[0].customerCode").value("CUS-001"))
                .andExpect(jsonPath("$.items[0].customerAlias").value("Brycen VN"))
                .andExpect(jsonPath("$.items[0].classification").value("INTERNAL"))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(51))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void get_returnsCustomerDetail() throws Exception {
        when(service.get(eq(CUSTOMER_ID), any(AppUser.class))).thenReturn(activeCustomer());

        mockMvc.perform(get("/api/v1/customers/{customerId}", CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.organizationId").value(ORG_ID.toString()))
                .andExpect(jsonPath("$.organizationName").value("Brycen Vietnam"))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void create_returnsCustomerDtoAndPassesPayloadToService() throws Exception {
        when(service.create(eq(ORG_ID), eq("CUS-NEW"), eq("New Alias"), eq("EXTERNAL"), any(AppUser.class)))
                .thenReturn(customer("CUS-NEW", "New Alias", Customer.CustomerClassification.EXTERNAL, 0L));

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBody(ORG_ID, "CUS-NEW", "New Alias", "EXTERNAL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerCode").value("CUS-NEW"))
                .andExpect(jsonPath("$.customerAlias").value("New Alias"))
                .andExpect(jsonPath("$.classification").value("EXTERNAL"));
    }

    @Test
    void update_passesOptimisticLockVersionToService() throws Exception {
        when(service.update(eq(CUSTOMER_ID), eq(ORG_ID), eq("CUS-UPD"), eq("Updated Alias"), eq("INTERNAL"), eq(7L), any(AppUser.class)))
                .thenReturn(customer("CUS-UPD", "Updated Alias", Customer.CustomerClassification.INTERNAL, 8L));

        mockMvc.perform(put("/api/v1/customers/{customerId}", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateBody(ORG_ID, "CUS-UPD", "Updated Alias", "INTERNAL", 7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerCode").value("CUS-UPD"))
                .andExpect(jsonPath("$.customerAlias").value("Updated Alias"))
                .andExpect(jsonPath("$.version").value(8));

        verify(service).update(eq(CUSTOMER_ID), eq(ORG_ID), eq("CUS-UPD"), eq("Updated Alias"), eq("INTERNAL"), eq(7L), any(AppUser.class));
    }

    @Test
    void softDelete_passesVersionAndReturnsDeletedCustomer() throws Exception {
        Customer deleted = activeCustomer();
        deleted.setStatus(Customer.CustomerStatus.DELETED);
        deleted.setDeletedAt(OffsetDateTime.parse("2026-02-01T00:00:00Z"));
        deleted.setVersion(4L);
        when(service.softDelete(eq(CUSTOMER_ID), eq(3L), any(AppUser.class))).thenReturn(deleted);

        mockMvc.perform(patch("/api/v1/customers/{customerId}/delete", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeleteBody(3L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andExpect(jsonPath("$.version").value(4));
    }

    @Test
    void serviceForbidden_mapsTo403WithMessageKey() throws Exception {
        when(service.get(eq(CUSTOMER_ID), any(AppUser.class)))
                .thenThrow(new ForbiddenException("Pages.Customer.Error.Forbidden"));

        mockMvc.perform(get("/api/v1/customers/{customerId}", CUSTOMER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Pages.Customer.Error.Forbidden"));
    }

    @Test
    void businessRuleException_mapsTo400WithMessageKey() throws Exception {
        when(service.create(eq(ORG_ID), eq("CUS-001"), eq("Alias"), eq("INTERNAL"), any(AppUser.class)))
                .thenThrow(new BusinessRuleException("Pages.Customer.Code.Duplicate"));

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBody(ORG_ID, "CUS-001", "Alias", "INTERNAL"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DOMAIN_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Pages.Customer.Code.Duplicate"));
    }

    @Test
    void optimisticLockingException_mapsTo409WithMessageKey() throws Exception {
        when(service.update(eq(CUSTOMER_ID), eq(ORG_ID), eq("CUS-001"), eq("Alias"), eq("INTERNAL"), eq(1L), any(AppUser.class)))
                .thenThrow(new OptimisticLockingException("Pages.Customer.Conflict.Version"));

        mockMvc.perform(put("/api/v1/customers/{customerId}", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateBody(ORG_ID, "CUS-001", "Alias", "INTERNAL", 1L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Pages.Customer.Conflict.Version"));
    }

    @Test
    void notFoundException_mapsTo404WithMessageKey() throws Exception {
        when(service.get(eq(CUSTOMER_ID), any(AppUser.class)))
                .thenThrow(new NotFoundException("Pages.Customer.NotFound"));

        mockMvc.perform(get("/api/v1/customers/{customerId}", CUSTOMER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Pages.Customer.NotFound"));
    }

    private AppUser adminUser() {
        return AppUser.builder()
                .email("admin@example.com")
                .displayName("Admin User")
                .role(AppUser.Role.ADMIN)
                .active(true)
                .build();
    }

    private Customer activeCustomer() {
        return customer("CUS-001", "Brycen VN", Customer.CustomerClassification.INTERNAL, 3L);
    }

    private Customer customer(String code, String alias, Customer.CustomerClassification classification, long version) {
        return Customer.builder()
                .customerId(CUSTOMER_ID)
                .organizationId(ORG_ID)
                .organizationName("Brycen Vietnam")
                .customerCode(code)
                .customerAlias(alias)
                .classification(classification)
                .status(Customer.CustomerStatus.ACTIVE)
                .createdAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                .createdBy("admin@example.com")
                .updatedAt(OffsetDateTime.parse("2026-01-02T00:00:00Z"))
                .updatedBy("admin@example.com")
                .version(version)
                .build();
    }

    private record CreateBody(UUID organizationId, String customerCode, String customerAlias, String classification) {
    }

    private record UpdateBody(UUID organizationId, String customerCode, String customerAlias, String classification, long version) {
    }

    private record DeleteBody(long version) {
    }

    private static final class FixedCurrentUserResolver implements HandlerMethodArgumentResolver {
        private final AppUser user;

        private FixedCurrentUserResolver(AppUser user) {
            this.user = user;
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
                WebDataBinderFactory binderFactory
        ) {
            return user;
        }
    }
}
