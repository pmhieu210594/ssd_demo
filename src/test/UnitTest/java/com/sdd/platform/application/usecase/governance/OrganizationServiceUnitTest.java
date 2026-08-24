package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.exception.OptimisticLockingException;
import com.sdd.platform.application.port.out.persistence.OrganizationRepositoryPort;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizationServiceUnitTest {

        private OrganizationRepositoryPort repository;
        private OrganizationService service;
        private AppUser admin;
        private AppUser viewer;

        @BeforeEach
        void setUp() {
                repository = Mockito.mock(OrganizationRepositoryPort.class);
                service = new OrganizationService(repository, Mockito.mock(AdminAuditLogService.class));
                admin = user(AppUser.Role.ADMIN, "admin@example.com");
                viewer = user(AppUser.Role.VIEWER, "viewer@example.com");
        }

        @Test
        void search_defaultsToActiveStatusAndNormalizesPageSize() {
                Organization active = organization(UUID.randomUUID(), "ORG-001", "Acme",
                                Organization.OrganizationStatus.ACTIVE, 0L);
                when(repository.findPage(isNull(), eq("ACTIVE"), eq(0), eq(20))).thenReturn(List.of(active));
                when(repository.count(isNull(), eq("ACTIVE"))).thenReturn(1L);

                var result = service.search("   ", null, -1, 0, admin);

                assertThat(result.items()).containsExactly(active);
                assertThat(result.page()).isZero();
                assertThat(result.size()).isEqualTo(20);
                assertThat(result.totalElements()).isEqualTo(1);
                assertThat(result.totalPages()).isEqualTo(1);
        }

        @Test
        void search_capsPageSizeAndSupportsDeletedFilter() {
                service.search(" deleted org ", "deleted", 2, 999, admin);

                verify(repository).findPage(eq("deleted org"), eq("DELETED"), eq(200), eq(100));
                verify(repository).count(eq("deleted org"), eq("DELETED"));
        }

        @Test
        void search_rejectsInvalidStatusFilter() {
                assertThatThrownBy(() -> service.search(null, "BROKEN", 0, 20, admin))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Organization.Status.Invalid");
        }

        @Test
        void search_requiresAdminCaller() {
                assertThatThrownBy(() -> service.search(null, null, 0, 20, viewer))
                                .isInstanceOf(ForbiddenException.class)
                                .hasMessage("Component.Permission.Denied");
        }

        @Test
        void get_returnsOrganizationForAdmin() {
                UUID id = UUID.randomUUID();
                Organization organization = organization(id, "ORG-001", "Acme", Organization.OrganizationStatus.ACTIVE,
                                0L);
                when(repository.findById(id)).thenReturn(Optional.of(organization));

                assertThat(service.get(id, admin)).isSameAs(organization);
        }

        @Test
        void get_throwsNotFoundWhenMissing() {
                UUID id = UUID.randomUUID();
                when(repository.findById(id)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.get(id, admin))
                                .isInstanceOf(NotFoundException.class)
                                .hasMessage("Pages.Organization.NotFound");
        }

        @Test
        void create_trimsValuesDefaultsActiveAndPersistsActor() {
                when(repository.insert(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

                Organization created = service.create(" ORG-NEW ", " New Organization ", " Description ", admin);

                assertThat(created.getOrganizationId()).isNotNull();
                assertThat(created.getOrganizationCode()).isEqualTo("ORG-NEW");
                assertThat(created.getOrganizationName()).isEqualTo("New Organization");
                assertThat(created.getDescription()).isEqualTo("Description");
                assertThat(created.getStatus()).isEqualTo(Organization.OrganizationStatus.ACTIVE);
                assertThat(created.getCreatedBy()).isEqualTo("admin@example.com");
                assertThat(created.getUpdatedBy()).isEqualTo("admin@example.com");
                assertThat(created.getVersion()).isZero();
                verify(repository).existsActiveCode("ORG-NEW", null);
                verify(repository).existsActiveName("New Organization", null);
        }

        @Test
        void create_rejectsDuplicateActiveCode() {
                when(repository.existsActiveCode("ORG-DUP", null)).thenReturn(true);

                assertThatThrownBy(() -> service.create("ORG-DUP", "Name", null, admin))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Organization.Code.Duplicate");

                verify(repository, never()).insert(any());
        }

        @Test
        void create_rejectsDuplicateActiveName() {
                when(repository.existsActiveName("Duplicate Name", null)).thenReturn(true);

                assertThatThrownBy(() -> service.create("ORG-OK", "Duplicate Name", null, admin))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Organization.Name.Duplicate");

                verify(repository, never()).insert(any());
        }

        @Test
        void create_rejectsRequiredAndMaxLengthViolations() {
                assertThatThrownBy(() -> service.create(" ", "Name", null, admin))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Organization.Code.Required");
                assertThatThrownBy(() -> service.create("ORG", " ", null, admin))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Organization.Name.Required");
                assertThatThrownBy(() -> service.create("A".repeat(51), "Name", null, admin))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Organization.Code.MaxLength");
                assertThatThrownBy(() -> service.create("ORG", "N".repeat(256), null, admin))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Organization.Name.MaxLength");
                assertThatThrownBy(() -> service.create("ORG", "Name", "D".repeat(501), admin))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Organization.Description.MaxLength");
        }

        @Test
        void update_successUpdatesEditableActiveOrganizationAndUsesVersion() {
                UUID id = UUID.randomUUID();
                Organization existing = organization(id, "OLD", "Old", Organization.OrganizationStatus.ACTIVE, 2L);
                Organization persisted = organization(id, "NEW", "New", Organization.OrganizationStatus.ACTIVE, 3L);
                when(repository.findById(id)).thenReturn(Optional.of(existing), Optional.of(persisted));
                when(repository.update(any(Organization.class))).thenReturn(1);

                Organization result = service.update(id, " NEW ", " New ", " Desc ", "ACTIVE", 2L, admin);

                assertThat(result).isSameAs(persisted);
                ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
                verify(repository).update(captor.capture());
                Organization updated = captor.getValue();
                assertThat(updated.getOrganizationCode()).isEqualTo("NEW");
                assertThat(updated.getOrganizationName()).isEqualTo("New");
                assertThat(updated.getDescription()).isEqualTo("Desc");
                assertThat(updated.getStatus()).isEqualTo(Organization.OrganizationStatus.ACTIVE);
                assertThat(updated.getVersion()).isEqualTo(2L);
                assertThat(updated.getDeletedAt()).isNull();
                assertThat(updated.getDeletedBy()).isNull();
        }

        @Test
        void update_rejectsSoftDeletedTarget() {
                UUID id = UUID.randomUUID();
                when(repository.findById(id)).thenReturn(Optional
                                .of(organization(id, "DEL", "Deleted", Organization.OrganizationStatus.DELETED, 1L)));

                assertThatThrownBy(() -> service.update(id, "ORG", "Name", null, "ACTIVE", 1L, admin))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Organization.Deleted.EditNotAllowed");
        }

        @Test
        void update_rejectsDuplicateCodeAndDuplicateName() {
                UUID id = UUID.randomUUID();
                when(repository.findById(id)).thenReturn(Optional
                                .of(organization(id, "OLD", "Old", Organization.OrganizationStatus.ACTIVE, 1L)));
                when(repository.existsActiveCode("DUP-CODE", id)).thenReturn(true);

                assertThatThrownBy(() -> service.update(id, "DUP-CODE", "Name", null, "ACTIVE", 1L, admin))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Organization.Code.DuplicateOnUpdate");

                when(repository.existsActiveCode("OK", id)).thenReturn(false);
                when(repository.existsActiveName("DUP-NAME", id)).thenReturn(true);
                assertThatThrownBy(() -> service.update(id, "OK", "DUP-NAME", null, "ACTIVE", 1L, admin))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Organization.Name.Duplicate");
        }

        @Test
        void update_throwsConflictWhenRepositoryAffectsNoRows() {
                UUID id = UUID.randomUUID();
                when(repository.findById(id)).thenReturn(Optional
                                .of(organization(id, "OLD", "Old", Organization.OrganizationStatus.ACTIVE, 1L)));
                when(repository.update(any(Organization.class))).thenReturn(0);

                assertThatThrownBy(() -> service.update(id, "ORG", "Name", null, "ACTIVE", 1L, admin))
                                .isInstanceOf(OptimisticLockingException.class)
                                .hasMessage("Pages.Organization.Conflict.Version");
        }

        @Test
        void softDelete_marksDeletedMetadataAndReturnsReloadedOrganization() {
                UUID id = UUID.randomUUID();
                Organization existing = organization(id, "ORG", "Name", Organization.OrganizationStatus.ACTIVE, 4L);
                Organization deleted = organization(id, "ORG", "Name", Organization.OrganizationStatus.DELETED, 5L);
                when(repository.findById(id)).thenReturn(Optional.of(existing), Optional.of(deleted));
                when(repository.softDelete(eq(id), eq(4L), eq("admin@example.com"), any(), eq("admin@example.com"),
                                any()))
                                .thenReturn(1);

                Organization result = service.softDelete(id, 4L, admin);

                assertThat(result).isSameAs(deleted);
                verify(repository).softDelete(eq(id), eq(4L), eq("admin@example.com"), any(), eq("admin@example.com"),
                                any());
        }

        @Test
        void softDelete_rejectsAlreadyDeletedOrganization() {
                UUID id = UUID.randomUUID();
                when(repository.findById(id)).thenReturn(Optional
                                .of(organization(id, "ORG", "Name", Organization.OrganizationStatus.DELETED, 4L)));

                assertThatThrownBy(() -> service.softDelete(id, 4L, admin))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Organization.Deleted.AlreadyDeleted");
        }

        @Test
        void softDelete_throwsConflictWhenVersionIsStale() {
                UUID id = UUID.randomUUID();
                when(repository.findById(id)).thenReturn(Optional
                                .of(organization(id, "ORG", "Name", Organization.OrganizationStatus.ACTIVE, 4L)));
                when(repository.softDelete(eq(id), eq(4L), any(), any(), any(), any())).thenReturn(0);

                assertThatThrownBy(() -> service.softDelete(id, 4L, admin))
                                .isInstanceOf(OptimisticLockingException.class)
                                .hasMessage("Pages.Organization.Conflict.Version");
        }

        private AppUser user(AppUser.Role role, String email) {
                return AppUser.builder()
                                .id(1L)
                                .provider("google")
                                .providerUid(email)
                                .email(email)
                                .displayName(email)
                                .role(role)
                                .active(true)
                                .build();
        }

        private Organization organization(UUID id, String code, String name, Organization.OrganizationStatus status,
                        long version) {
                OffsetDateTime now = OffsetDateTime.parse("2026-06-10T00:00:00Z");
                return Organization.builder()
                                .organizationId(id)
                                .organizationCode(code)
                                .organizationName(name)
                                .description("Description")
                                .status(status)
                                .createdAt(now)
                                .createdBy("tester")
                                .updatedAt(now)
                                .updatedBy("tester")
                                .deletedAt(status == Organization.OrganizationStatus.DELETED ? now : null)
                                .deletedBy(status == Organization.OrganizationStatus.DELETED ? "tester" : null)
                                .version(version)
                                .build();
        }
}
