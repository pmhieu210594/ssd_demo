package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.exception.OptimisticLockingException;
import com.sdd.platform.application.port.out.persistence.CustomerRepositoryPort;
import com.sdd.platform.application.port.out.persistence.OrganizationRepositoryPort;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.Customer;
import com.sdd.platform.domain.model.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OTHER_ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");

    @Mock
    private CustomerRepositoryPort customerRepository;

    @Mock
    private OrganizationRepositoryPort organizationRepository;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(customerRepository, organizationRepository, adminAuditLogService);
    }

    @Test
    void search_normalizesFiltersPagingAndDefaultsToActiveStatus() {
        AppUser caller = adminUser();
        Customer customer = activeCustomer();
        when(customerRepository.findPage(ORG_ID, "Brycen", "INTERNAL", "ACTIVE", 100, 100))
                .thenReturn(List.of(customer));
        when(customerRepository.count(ORG_ID, "Brycen", "INTERNAL", "ACTIVE"))
                .thenReturn(101L);

        PageResult<Customer> result = service.search(ORG_ID, "  Brycen  ", " internal ", null, 1, 500, caller);

        assertThat(result.items()).containsExactly(customer);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.totalElements()).isEqualTo(101L);
        assertThat(result.totalPages()).isEqualTo(2);
        verify(customerRepository).findPage(ORG_ID, "Brycen", "INTERNAL", "ACTIVE", 100, 100);
        verify(customerRepository).count(ORG_ID, "Brycen", "INTERNAL", "ACTIVE");
    }

    @Test
    void search_acceptsAllStatusAndClassificationForAdministrativeReview() {
        AppUser caller = adminUser();
        when(customerRepository.findPage(null, null, "ALL", "ALL", 0, 20)).thenReturn(List.of());
        when(customerRepository.count(null, null, "ALL", "ALL")).thenReturn(0L);

        PageResult<Customer> result = service.search(null, " ", "ALL", "ALL", -1, 0, caller);

        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isZero();
        verify(customerRepository).findPage(null, null, "ALL", "ALL", 0, 20);
        verify(customerRepository).count(null, null, "ALL", "ALL");
    }

    @Test
    void search_rejectsInvalidStatusFilter() {
        assertThatThrownBy(() -> service.search(null, null, null, "DISABLED", 0, 20, adminUser()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.Customer.Status.Invalid");
    }

    @Test
    void nonAdminIsDeniedForEveryEntryPoint() {
        AppUser viewer = AppUser.builder().role(AppUser.Role.VIEWER).active(true).build();

        assertThatThrownBy(() -> service.search(null, null, null, null, 0, 20, viewer))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Pages.Customer.Error.Forbidden");
        assertThatThrownBy(() -> service.get(CUSTOMER_ID, viewer))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Pages.Customer.Error.Forbidden");
        assertThatThrownBy(() -> service.create(ORG_ID, "C001", "Alias", "INTERNAL", viewer))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Pages.Customer.Error.Forbidden");
        assertThatThrownBy(() -> service.update(CUSTOMER_ID, ORG_ID, "C001", "Alias", "INTERNAL", 1L, viewer))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Pages.Customer.Error.Forbidden");
        assertThatThrownBy(() -> service.softDelete(CUSTOMER_ID, 1L, viewer))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Pages.Customer.Error.Forbidden");
    }

    @Test
    void get_returnsExistingCustomerForAdmin() {
        Customer customer = activeCustomer();
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        Customer result = service.get(CUSTOMER_ID, adminUser());

        assertThat(result).isSameAs(customer);
    }

    @Test
    void get_throwsNotFoundWhenCustomerMissing() {
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(CUSTOMER_ID, adminUser()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Pages.Customer.NotFound");
    }

    @Test
    void create_trimsValuesDefaultsInternalAndAssignsOrganizationAndActor() {
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(activeOrganization()));
        when(customerRepository.existsActiveCode("CUS-001", null)).thenReturn(false);
        when(customerRepository.existsActiveAlias(ORG_ID, "Brycen VN", null)).thenReturn(false);
        when(customerRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Customer created = service.create(ORG_ID, "  CUS-001  ", "  Brycen VN  ", " ", adminUser());

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).insert(captor.capture());
        Customer saved = captor.getValue();
        assertThat(saved.getCustomerId()).isNotNull();
        assertThat(saved.getOrganizationId()).isEqualTo(ORG_ID);
        assertThat(saved.getOrganizationName()).isEqualTo("Brycen Vietnam");
        assertThat(saved.getCustomerCode()).isEqualTo("CUS-001");
        assertThat(saved.getCustomerAlias()).isEqualTo("Brycen VN");
        assertThat(saved.getClassification()).isEqualTo(Customer.CustomerClassification.INTERNAL);
        assertThat(saved.getStatus()).isEqualTo(Customer.CustomerStatus.ACTIVE);
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getCreatedBy()).isEqualTo("admin@example.com");
        assertThat(saved.getUpdatedBy()).isEqualTo("admin@example.com");
        assertThat(created).isSameAs(saved);
    }

    @Test
    void create_rejectsMissingOrganization() {
        assertThatThrownBy(() -> service.create(null, "CUS-001", "Alias", "INTERNAL", adminUser()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.Customer.Organization.Required");
        verify(organizationRepository, never()).findById(any());
    }

    @Test
    void create_rejectsDeletedOrganization() {
        Organization deletedOrganization = activeOrganization();
        deletedOrganization.setStatus(Organization.OrganizationStatus.DELETED);
        deletedOrganization.setDeletedAt(OffsetDateTime.now());
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(deletedOrganization));

        assertThatThrownBy(() -> service.create(ORG_ID, "CUS-001", "Alias", "INTERNAL", adminUser()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.Customer.Organization.Unavailable");
        verify(customerRepository, never()).insert(any());
    }

    @Test
    void create_rejectsMissingAndTooLongCode() {
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(activeOrganization()));

        assertThatThrownBy(() -> service.create(ORG_ID, " ", "Alias", "INTERNAL", adminUser()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.Customer.Code.Required");
        assertThatThrownBy(() -> service.create(ORG_ID, "C".repeat(51), "Alias", "INTERNAL", adminUser()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.Customer.Code.MaxLength");
    }

    @Test
    void create_rejectsMissingAndTooLongAlias() {
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(activeOrganization()));

        assertThatThrownBy(() -> service.create(ORG_ID, "CUS-001", " ", "INTERNAL", adminUser()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.Customer.Alias.Required");
        assertThatThrownBy(() -> service.create(ORG_ID, "CUS-001", "A".repeat(256), "INTERNAL", adminUser()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.Customer.Alias.MaxLength");
    }

    @Test
    void create_rejectsInvalidClassification() {
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(activeOrganization()));

        assertThatThrownBy(() -> service.create(ORG_ID, "CUS-001", "Alias", "partner", adminUser()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.Customer.Classification.Invalid");
    }

    @Test
    void create_rejectsDuplicateActiveCodeGloballyAndDuplicateAliasWithinOrganization() {
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(activeOrganization()));
        when(customerRepository.existsActiveCode("CUS-001", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(ORG_ID, "CUS-001", "Alias", "INTERNAL", adminUser()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.Customer.Code.Duplicate");

        when(customerRepository.existsActiveCode("CUS-002", null)).thenReturn(false);
        when(customerRepository.existsActiveAlias(ORG_ID, "Alias", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(ORG_ID, "CUS-002", "Alias", "INTERNAL", adminUser()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.Customer.Alias.Duplicate");
    }

    @Test
    void create_allowsReusedCodeAndAliasAcrossOrganizationsWhenActiveScopeChecksPass() {
        when(organizationRepository.findById(OTHER_ORG_ID)).thenReturn(Optional.of(otherActiveOrganization()));
        when(customerRepository.existsActiveCode("CUS-001", null)).thenReturn(false);
        when(customerRepository.existsActiveAlias(OTHER_ORG_ID, "Brycen VN", null)).thenReturn(false);
        when(customerRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Customer created = service.create(OTHER_ORG_ID, "CUS-001", "Brycen VN", "EXTERNAL", adminUser());

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).insert(captor.capture());
        Customer saved = captor.getValue();
        assertThat(saved.getOrganizationId()).isEqualTo(OTHER_ORG_ID);
        assertThat(saved.getOrganizationName()).isEqualTo("Other Organization");
        assertThat(saved.getCustomerCode()).isEqualTo("CUS-001");
        assertThat(saved.getCustomerAlias()).isEqualTo("Brycen VN");
        assertThat(saved.getClassification()).isEqualTo(Customer.CustomerClassification.EXTERNAL);
        assertThat(created).isSameAs(saved);
    }

    @Test
    void update_changesEditableCustomerAndUsesSubmittedVersionForOptimisticLocking() {
        Customer existing = activeCustomer();
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(existing));
        when(organizationRepository.findById(OTHER_ORG_ID)).thenReturn(Optional.of(otherActiveOrganization()));
        when(customerRepository.existsActiveCode("CUS-002", CUSTOMER_ID)).thenReturn(false);
        when(customerRepository.existsActiveAlias(OTHER_ORG_ID, "Updated Alias", CUSTOMER_ID)).thenReturn(false);
        when(customerRepository.update(any())).thenReturn(1);

        Customer updated = service.update(CUSTOMER_ID, OTHER_ORG_ID, " CUS-002 ", " Updated Alias ", "EXTERNAL", 7L, adminUser());

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).update(captor.capture());
        Customer saved = captor.getValue();
        assertThat(saved.getOrganizationId()).isEqualTo(OTHER_ORG_ID);
        assertThat(saved.getOrganizationName()).isEqualTo("Other Organization");
        assertThat(saved.getCustomerCode()).isEqualTo("CUS-002");
        assertThat(saved.getCustomerAlias()).isEqualTo("Updated Alias");
        assertThat(saved.getClassification()).isEqualTo(Customer.CustomerClassification.EXTERNAL);
        assertThat(saved.getVersion()).isEqualTo(7L);
        assertThat(saved.getUpdatedBy()).isEqualTo("admin@example.com");
        assertThat(updated).isSameAs(existing);
    }

    @Test
    void update_allowsReusedCodeAndAliasAcrossOrganizationsWhenActiveScopeChecksPass() {
        Customer existing = activeCustomer();
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(existing));
        when(organizationRepository.findById(OTHER_ORG_ID)).thenReturn(Optional.of(otherActiveOrganization()));
        when(customerRepository.existsActiveCode("CUS-001", CUSTOMER_ID)).thenReturn(false);
        when(customerRepository.existsActiveAlias(OTHER_ORG_ID, "Brycen VN", CUSTOMER_ID)).thenReturn(false);
        when(customerRepository.update(any())).thenReturn(1);

        Customer updated = service.update(CUSTOMER_ID, OTHER_ORG_ID, "CUS-001", "Brycen VN", "INTERNAL", 7L, adminUser());

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).update(captor.capture());
        Customer saved = captor.getValue();
        assertThat(saved.getOrganizationId()).isEqualTo(OTHER_ORG_ID);
        assertThat(saved.getOrganizationName()).isEqualTo("Other Organization");
        assertThat(saved.getCustomerCode()).isEqualTo("CUS-001");
        assertThat(saved.getCustomerAlias()).isEqualTo("Brycen VN");
        assertThat(saved.getClassification()).isEqualTo(Customer.CustomerClassification.INTERNAL);
        assertThat(saved.getVersion()).isEqualTo(7L);
        assertThat(updated).isSameAs(existing);
    }

    @Test
    void update_rejectsDeletedCustomer() {
        Customer deleted = activeCustomer();
        deleted.setStatus(Customer.CustomerStatus.DELETED);
        deleted.setDeletedAt(OffsetDateTime.now());
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.update(CUSTOMER_ID, ORG_ID, "CUS-002", "Alias", "INTERNAL", 1L, adminUser()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.Customer.Deleted.CannotEdit");
        verify(customerRepository, never()).update(any());
    }

    @Test
    void update_rejectsStaleVersionWhenRepositoryUpdatesNoRows() {
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(activeCustomer()));
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(activeOrganization()));
        when(customerRepository.existsActiveCode("CUS-001", CUSTOMER_ID)).thenReturn(false);
        when(customerRepository.existsActiveAlias(ORG_ID, "Brycen VN", CUSTOMER_ID)).thenReturn(false);
        when(customerRepository.update(any())).thenReturn(0);

        assertThatThrownBy(() -> service.update(CUSTOMER_ID, ORG_ID, "CUS-001", "Brycen VN", "INTERNAL", 0L, adminUser()))
                .isInstanceOf(OptimisticLockingException.class)
                .hasMessage("Pages.Customer.Conflict.Version");
    }

    @Test
    void softDelete_marksActiveCustomerDeletedUsingVersionAndActor() {
        Customer deleted = activeCustomer();
        deleted.setStatus(Customer.CustomerStatus.DELETED);
        deleted.setDeletedAt(OffsetDateTime.now());
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(activeCustomer()), Optional.of(deleted));
        when(customerRepository.softDelete(eq(CUSTOMER_ID), eq(3L), anyString(), any(OffsetDateTime.class), anyString(), any(OffsetDateTime.class)))
                .thenReturn(1);

        Customer result = service.softDelete(CUSTOMER_ID, 3L, adminUser());

        assertThat(result.getStatus()).isEqualTo(Customer.CustomerStatus.DELETED);
        verify(customerRepository).softDelete(eq(CUSTOMER_ID), eq(3L), eq("admin@example.com"), any(OffsetDateTime.class), eq("admin@example.com"), any(OffsetDateTime.class));
    }

    @Test
    void softDelete_rejectsAlreadyDeletedAndStaleVersion() {
        Customer deleted = activeCustomer();
        deleted.setStatus(Customer.CustomerStatus.DELETED);
        deleted.setDeletedAt(OffsetDateTime.now());
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.softDelete(CUSTOMER_ID, 3L, adminUser()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.Customer.AlreadyDeleted");

        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(activeCustomer()));
        when(customerRepository.softDelete(eq(CUSTOMER_ID), eq(99L), anyString(), any(OffsetDateTime.class), anyString(), any(OffsetDateTime.class)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.softDelete(CUSTOMER_ID, 99L, adminUser()))
                .isInstanceOf(OptimisticLockingException.class)
                .hasMessage("Pages.Customer.Conflict.Version");
    }

    private AppUser adminUser() {
        return AppUser.builder()
                .email("admin@example.com")
                .displayName("Admin User")
                .role(AppUser.Role.ADMIN)
                .active(true)
                .build();
    }

    private Organization activeOrganization() {
        return Organization.builder()
                .organizationId(ORG_ID)
                .organizationCode("ORG-001")
                .organizationName("Brycen Vietnam")
                .status(Organization.OrganizationStatus.ACTIVE)
                .version(1L)
                .build();
    }

    private Organization otherActiveOrganization() {
        return Organization.builder()
                .organizationId(OTHER_ORG_ID)
                .organizationCode("ORG-002")
                .organizationName("Other Organization")
                .status(Organization.OrganizationStatus.ACTIVE)
                .version(1L)
                .build();
    }

    private Customer activeCustomer() {
        return Customer.builder()
                .customerId(CUSTOMER_ID)
                .organizationId(ORG_ID)
                .organizationName("Brycen Vietnam")
                .customerCode("CUS-001")
                .customerAlias("Brycen VN")
                .classification(Customer.CustomerClassification.INTERNAL)
                .status(Customer.CustomerStatus.ACTIVE)
                .createdAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                .createdBy("admin@example.com")
                .updatedAt(OffsetDateTime.parse("2026-01-02T00:00:00Z"))
                .updatedBy("admin@example.com")
                .version(3L)
                .build();
    }
}
