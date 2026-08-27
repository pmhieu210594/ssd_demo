package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.RoleRepositoryPort;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.Role;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleServiceUnitTest {

    private RoleRepositoryPort repository;
    private RoleService service;
    private AppUser admin;
    private AppUser editor;
    private AppUser viewer;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(RoleRepositoryPort.class);
        service = new RoleService(repository);
        admin = user(AppUser.Role.ADMIN, "admin@example.com");
        editor = user(AppUser.Role.EDITOR, "editor@example.com");
        viewer = user(AppUser.Role.VIEWER, "viewer@example.com");
    }

    @Test
    void list_allowsViewerAndNormalizesSearchAndSort() {
        Role role = role(UUID.randomUUID(), "PM");
        when(repository.findActive(eq("pm"), eq("roleNameDesc"))).thenReturn(List.of(role));

        List<Role> result = service.list(" pm ", "-roleName", viewer);

        assertThat(result).containsExactly(role);
        verify(repository).findActive("pm", "roleNameDesc");
    }

    @Test
    void create_trimsFieldsAndAllowsEditor() {
        when(repository.insert(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Role created = service.create(" PM ", " Description ", editor);

        assertThat(created.getRoleName()).isEqualTo("PM");
        assertThat(created.getDescription()).isEqualTo("Description");
        assertThat(created.getCreatedBy()).isEqualTo("editor@example.com");
        verify(repository).existsActiveName("PM", null);
    }

    @Test
    void create_keepsTrimmedEmptyDescriptionAsEmptyString() {
        when(repository.insert(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Role created = service.create("QA", "   ", admin);

        assertThat(created.getDescription()).isEmpty();
    }

    @Test
    void create_rejectsDuplicateAsBusinessRule() {
        when(repository.existsActiveName("PM", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create("PM", "", admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.RoleManagement.RoleName.Duplicate");

        verify(repository, never()).insert(any());
    }

    @Test
    void create_allowsNameThatOnlyMatchesLogicallyDeletedRows() {
        when(repository.insert(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.existsActiveName("PM", null)).thenReturn(false);

        Role created = service.create("PM", "", admin);

        assertThat(created.getRoleName()).isEqualTo("PM");
        verify(repository).existsActiveName("PM", null);
    }

    @Test
    void create_rejectsBlankRoleName() {
        assertThatThrownBy(() -> service.create("   ", "", admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.RoleManagement.RoleName.Required");

        verify(repository, never()).insert(any());
    }

    @Test
    void create_rejectsViewer() {
        assertThatThrownBy(() -> service.create("PM", "", viewer))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Component.Permission.Denied");
    }

    @Test
    void update_rejectsDuplicateExcludingSelf() {
        UUID id = UUID.randomUUID();
        when(repository.findActiveById(id)).thenReturn(Optional.of(role(id, "PM")));
        when(repository.existsActiveName("QA", id)).thenReturn(true);

        assertThatThrownBy(() -> service.update(id, "QA", "", editor))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.RoleManagement.RoleName.Duplicate");
    }

    @Test
    void update_allowsNameThatOnlyMatchesLogicallyDeletedRows() {
        UUID id = UUID.randomUUID();
        Role existing = role(id, "PM");
        when(repository.findActiveById(id)).thenReturn(Optional.of(existing), Optional.of(existing));
        when(repository.existsActiveName("QA", id)).thenReturn(false);
        when(repository.update(any(Role.class))).thenReturn(1);

        Role updated = service.update(id, "QA", "", editor);

        assertThat(updated.getRoleName()).isEqualTo("QA");
        verify(repository).existsActiveName("QA", id);
    }

    @Test
    void logicalDelete_allowsAdminAndUpdatesFlagThroughRepository() {
        UUID id = UUID.randomUUID();
        Role deleted = role(id, "PM");
        deleted.setDeleteFlag(1);
        when(repository.findActiveById(id)).thenReturn(Optional.of(role(id, "PM")));
        when(repository.logicalDelete(eq(id), eq("admin@example.com"), any())).thenReturn(1);
        when(repository.findById(id)).thenReturn(Optional.of(deleted));

        Role result = service.logicalDelete(id, admin);

        assertThat(result).isSameAs(deleted);
        verify(repository).logicalDelete(eq(id), eq("admin@example.com"), any());
        verify(repository).findById(id);
    }

    @Test
    void logicalDelete_rejectsEditorAndViewer() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.logicalDelete(id, editor))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.logicalDelete(id, viewer))
                .isInstanceOf(ForbiddenException.class);

        verify(repository, never()).logicalDelete(any(), any(), any());
    }

    @Test
    void update_rejectsViewer() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.update(id, "PM", "", viewer))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Component.Permission.Denied");

        verify(repository, never()).findActiveById(any());
        verify(repository, never()).update(any());
    }

    @Test
    void get_throwsNotFoundForMissingRole() {
        UUID id = UUID.randomUUID();
        when(repository.findActiveById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id, admin))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Pages.RoleManagement.NotFound");
    }

    @Test
    void update_persistsTrimmedValues() {
        UUID id = UUID.randomUUID();
        Role existing = role(id, "PM");
        Role persisted = role(id, "QA");
        when(repository.findActiveById(id)).thenReturn(Optional.of(existing), Optional.of(persisted));
        when(repository.update(any(Role.class))).thenReturn(1);

        Role result = service.update(id, " QA ", " Desc ", editor);

        assertThat(result).isSameAs(persisted);
        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(repository).update(captor.capture());
        assertThat(captor.getValue().getRoleName()).isEqualTo("QA");
        assertThat(captor.getValue().getDescription()).isEqualTo("Desc");
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo("editor@example.com");
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

    private Role role(UUID id, String name) {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-10T00:00:00Z");
        return Role.builder()
                .roleId(id)
                .roleName(name)
                .description("Description")
                .createdAt(now)
                .createdBy("tester")
                .updatedAt(now)
                .updatedBy("tester")
                .deleteFlag(0)
                .build();
    }
}
