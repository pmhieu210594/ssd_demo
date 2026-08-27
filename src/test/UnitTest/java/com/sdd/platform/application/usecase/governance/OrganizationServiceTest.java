package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.port.out.persistence.OrganizationRepositoryPort;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.Organization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepositoryPort repository;

    @InjectMocks
    private OrganizationService service;

    @Test
    void create_trimsValuesAndAssignsActor() {
        AppUser caller = adminUser();
        when(repository.existsActiveCode("ORG-001", null)).thenReturn(false);
        when(repository.existsActiveName("Acme", null)).thenReturn(false);
        when(repository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Organization created = service.create("  ORG-001  ", "  Acme  ", "  Desc  ", caller);

        ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
        verify(repository).insert(captor.capture());
        Organization saved = captor.getValue();

        assertThat(saved.getOrganizationId()).isNotNull();
        assertThat(saved.getOrganizationCode()).isEqualTo("ORG-001");
        assertThat(saved.getOrganizationName()).isEqualTo("Acme");
        assertThat(saved.getDescription()).isEqualTo("Desc");
        assertThat(saved.getStatus()).isEqualTo(Organization.OrganizationStatus.ACTIVE);
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getCreatedBy()).isEqualTo("admin@example.com");
        assertThat(created.getOrganizationId()).isEqualTo(saved.getOrganizationId());
    }

    @Test
    void search_normalizesPagingAndKeyword() {
        AppUser caller = adminUser();
        when(repository.findPage("Acme", "ACTIVE", 100, 100)).thenReturn(List.of());
        when(repository.count("Acme", "ACTIVE")).thenReturn(0L);

        PageResult<Organization> result = service.search("  Acme  ", null, 1, 500, caller);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.totalElements()).isZero();
        verify(repository).findPage("Acme", "ACTIVE", 100, 100);
        verify(repository).count("Acme", "ACTIVE");
    }

    @Test
    void update_rejectsStaleVersion() {
        AppUser caller = adminUser();
        UUID id = UUID.randomUUID();
        Organization existing = Organization.builder()
                .organizationId(id)
                .organizationCode("ORG-001")
                .organizationName("Acme")
                .status(Organization.OrganizationStatus.ACTIVE)
                .version(1L)
                .build();

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.existsActiveCode("ORG-002", id)).thenReturn(false);
        when(repository.existsActiveName("Beta", id)).thenReturn(false);
        when(repository.update(any())).thenReturn(0);

        assertThatThrownBy(() -> service.update(id, "ORG-002", "Beta", null, "ACTIVE", 0L, caller))
                .isInstanceOf(com.sdd.platform.application.exception.OptimisticLockingException.class)
                .hasMessage("Pages.Organization.Conflict.Version");
    }

    @Test
    void nonAdminIsDenied() {
        AppUser caller = AppUser.builder().role(AppUser.Role.VIEWER).build();

        assertThatThrownBy(() -> service.search(null, null, 0, 20, caller))
                .isInstanceOf(com.sdd.platform.application.exception.ForbiddenException.class)
                .hasMessage("Component.Permission.Denied");
    }

    private AppUser adminUser() {
        return AppUser.builder()
                .email("admin@example.com")
                .displayName("Admin User")
                .role(AppUser.Role.ADMIN)
                .active(true)
                .build();
    }
}
