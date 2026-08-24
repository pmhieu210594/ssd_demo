package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.UserAccountAdminRepositoryPort;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.RoleOption;
import com.sdd.platform.domain.model.UserAccountAdminView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAccountAdminServicePhase6Test {

    private UserAccountAdminRepositoryPort repository;
    private UserAccountAdminService service;
    private AppUser admin;
    private AppUser nonAdmin;
    private UUID adminRoleId;
    private UUID userRoleId;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(UserAccountAdminRepositoryPort.class);
        service = new UserAccountAdminService(repository, Mockito.mock(AdminAuditLogService.class));
        admin = AppUser.builder()
                .provider("internal")
                .providerUid("admin")
                .email("admin@example.com")
                .displayName("Admin User")
                .role(AppUser.Role.ADMIN)
                .active(true)
                .build();
        nonAdmin = AppUser.builder()
                .provider("internal")
                .providerUid("viewer")
                .email("viewer@example.com")
                .displayName("Viewer User")
                .role(AppUser.Role.VIEWER)
                .active(true)
                .build();
        adminRoleId = UUID.randomUUID();
        userRoleId = UUID.randomUUID();
        when(repository.findRoleById(adminRoleId)).thenReturn(Optional.of(role(adminRoleId, "ADMIN")));
        when(repository.findRoleById(userRoleId)).thenReturn(Optional.of(role(userRoleId, "USER")));
    }

    @Test
    void create_createsLinkedMemberWithRoleAndBcryptAccountPassword() {
        UUID storedAccountId = UUID.randomUUID();
        UUID storedMemberKey = UUID.randomUUID();
        when(repository.findById(any())).thenReturn(Optional.of(account(
                storedAccountId,
                storedMemberKey,
                "new.user",
                "New User",
                userRoleId,
                "USER",
                true
        )));

        service.create(
                " new.user ",
                " New User ",
                "new.user@example.com",
                "Password1!",
                "Password1!",
                userRoleId,
                true,
                admin
        );

        ArgumentCaptor<UUID> generatedMemberKey = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> passwordHash = ArgumentCaptor.forClass(String.class);
        verify(repository).insertMember(
                generatedMemberKey.capture(),
                eq(userRoleId),
                eq("new.user"),
                eq("admin@example.com"),
                any()
        );
        verify(repository).insertAccount(
                any(),
                eq(generatedMemberKey.getValue()),
                eq("new.user"),
                eq("New User"),
                eq("new.user@example.com"),
                passwordHash.capture(),
                eq("bcrypt"),
                eq(true),
                eq("admin@example.com"),
                any()
        );
        assertThat(passwordHash.getValue()).isNotEqualTo("Password1!");
        assertThat(new BCryptPasswordEncoder().matches("Password1!", passwordHash.getValue())).isTrue();
    }

    @Test
    void create_defaultsFullnameToUsernameAndInactiveWhenActiveFlagIsFalse() {
        when(repository.findById(any())).thenReturn(Optional.of(account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user02",
                "user02",
                userRoleId,
                "USER",
                false
        )));

        service.create(
                "user02",
                "   ",
                null,
                "Password1!",
                "Password1!",
                userRoleId,
                false,
                admin
        );

        verify(repository).insertAccount(
                any(),
                any(),
                eq("user02"),
                eq("user02"),
                isNull(),
                any(),
                eq("bcrypt"),
                eq(false),
                eq("admin@example.com"),
                any()
        );
    }

    @Test
    void create_rejectsWeakPasswordBeforeWritingMemberOrAccount() {
        assertThatThrownBy(() -> service.create(
                "weak.user",
                "Weak User",
                null,
                "password",
                "password",
                userRoleId,
                true,
                admin
        ))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.UserAccounts.Password.Pattern");

        verify(repository, never()).insertMember(any(), any(), any(), any(), any());
        verify(repository, never()).insertAccount(any(), any(), any(), any(), any(), any(), any(), eq(true), any(), any());
    }

    @Test
    void create_rejectsPasswordMismatchBeforeWritingMemberOrAccount() {
        assertThatThrownBy(() -> service.create(
                "mismatch.user",
                "Mismatch User",
                null,
                "Password1!",
                "Password2!",
                userRoleId,
                true,
                admin
        ))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.UserAccounts.Password.Mismatch");

        verify(repository, never()).insertMember(any(), any(), any(), any(), any());
        verify(repository, never()).insertAccount(any(), any(), any(), any(), any(), any(), any(), eq(true), any(), any());
    }

    @Test
    void create_rejectsDuplicateUsernameAfterTrimBeforeWritingMemberOrAccount() {
        when(repository.existsUsername("duplicate.user", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                " duplicate.user ",
                "Duplicate User",
                null,
                "Password1!",
                "Password1!",
                userRoleId,
                true,
                admin
        ))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.UserAccounts.Username.Duplicate");

        verify(repository).existsUsername("duplicate.user", null);
        verify(repository, never()).insertMember(any(), any(), any(), any(), any());
        verify(repository, never()).insertAccount(any(), any(), any(), any(), any(), any(), any(), eq(true), any(), any());
    }

    @Test
    void create_rejectsInvalidRoleBeforeWritingMemberOrAccount() {
        UUID missingRoleId = UUID.randomUUID();
        when(repository.findRoleById(missingRoleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                "missing.role",
                "Missing Role",
                null,
                "Password1!",
                "Password1!",
                missingRoleId,
                true,
                admin
        ))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.UserAccounts.Role.Invalid");

        verify(repository, never()).insertMember(any(), any(), any(), any(), any());
        verify(repository, never()).insertAccount(any(), any(), any(), any(), any(), any(), any(), eq(true), any(), any());
    }

    @Test
    void create_rejectsMissingRoleBeforeWritingMemberOrAccount() {
        assertThatThrownBy(() -> service.create(
                "missing.role",
                "Missing Role",
                null,
                "Password1!",
                "Password1!",
                null,
                true,
                admin
        ))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.UserAccounts.Role.Required");

        verify(repository, never()).insertMember(any(), any(), any(), any(), any());
        verify(repository, never()).insertAccount(any(), any(), any(), any(), any(), any(), any(), eq(true), any(), any());
    }

    @Test
    void update_changesRoleOnMemberAndDoesNotUpdatePassword() {
        UUID accountId = UUID.randomUUID();
        UUID memberKey = UUID.randomUUID();
        when(repository.findById(accountId)).thenReturn(
                Optional.of(account(accountId, memberKey, "user03", "Old Name", userRoleId, "USER", true)),
                Optional.of(account(accountId, memberKey, "user03", "New Name", adminRoleId, "ADMIN", true))
        );
        when(repository.updateAccount(eq(accountId), eq("New Name"), eq("user03@example.com"), eq(true), eq("admin@example.com"), any()))
                .thenReturn(1);
        when(repository.updateMemberRole(eq(memberKey), eq(adminRoleId), eq("admin@example.com"), any()))
                .thenReturn(1);

        service.update(accountId, " New Name ", "user03@example.com", adminRoleId, true, admin);

        verify(repository).updateAccount(eq(accountId), eq("New Name"), eq("user03@example.com"), eq(true), eq("admin@example.com"), any());
        verify(repository).updateMemberRole(eq(memberKey), eq(adminRoleId), eq("admin@example.com"), any());
        verify(repository, never()).updatePassword(any(), any(), any(), any(), any());
    }

    @Test
    void update_rejectsLastActiveAdminDowngrade() {
        UUID accountId = UUID.randomUUID();
        when(repository.findById(accountId)).thenReturn(Optional.of(account(
                accountId,
                UUID.randomUUID(),
                "admin01",
                "Admin",
                adminRoleId,
                "ADMIN",
                true
        )));
        when(repository.countActiveAdminsExcluding(accountId)).thenReturn(0L);

        assertThatThrownBy(() -> service.update(accountId, "Admin", "admin01@example.com", userRoleId, true, admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.UserAccounts.LastAdmin");

        verify(repository, never()).updateAccount(any(), any(), any(), anyBoolean(), any(), any());
        verify(repository, never()).updateMemberRole(any(), any(), any(), any());
    }

    @Test
    void activate_updatesStatusWithoutDeletingAccount() {
        UUID accountId = UUID.randomUUID();
        UUID memberKey = UUID.randomUUID();
        when(repository.findById(accountId)).thenReturn(
                Optional.of(account(accountId, memberKey, "user04", "User Four", userRoleId, "USER", false)),
                Optional.of(account(accountId, memberKey, "user04", "User Four", userRoleId, "USER", true))
        );
        when(repository.updateActive(eq(accountId), eq(true), eq("admin@example.com"), any())).thenReturn(1);

        UserAccountAdminView result = service.activate(accountId, admin);

        assertThat(result.isActive()).isTrue();
        verify(repository).updateActive(eq(accountId), eq(true), eq("admin@example.com"), any());
        verify(repository, never()).updateAccount(any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void deactivate_rejectsLastActiveAdmin() {
        UUID accountId = UUID.randomUUID();
        when(repository.findById(accountId)).thenReturn(Optional.of(account(
                accountId,
                UUID.randomUUID(),
                "admin01",
                "Admin",
                adminRoleId,
                "ADMIN",
                true
        )));
        when(repository.countActiveAdminsExcluding(accountId)).thenReturn(0L);

        assertThatThrownBy(() -> service.deactivate(accountId, admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.UserAccounts.LastAdmin");

        verify(repository, never()).updateActive(any(), eq(false), any(), any());
    }

    @Test
    void deactivate_updatesStatusWithoutDeletingAccount() {
        UUID accountId = UUID.randomUUID();
        UUID memberKey = UUID.randomUUID();
        when(repository.findById(accountId)).thenReturn(
                Optional.of(account(accountId, memberKey, "user05", "User Five", userRoleId, "USER", true)),
                Optional.of(account(accountId, memberKey, "user05", "User Five", userRoleId, "USER", false))
        );
        when(repository.updateActive(eq(accountId), eq(false), eq("admin@example.com"), any())).thenReturn(1);

        UserAccountAdminView result = service.deactivate(accountId, admin);

        assertThat(result.isActive()).isFalse();
        verify(repository).updateActive(eq(accountId), eq(false), eq("admin@example.com"), any());
        verify(repository, never()).updateAccount(any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void resetPasswordStoresNewBcryptHashOnly() {
        UUID accountId = UUID.randomUUID();
        when(repository.findById(accountId)).thenReturn(Optional.of(account(
                accountId,
                UUID.randomUUID(),
                "user06",
                "User Six",
                userRoleId,
                "USER",
                true
        )));
        when(repository.updatePassword(eq(accountId), any(), eq("bcrypt"), eq("admin@example.com"), any()))
                .thenReturn(1);

        service.resetPassword(accountId, "Password2!", "Password2!", admin);

        ArgumentCaptor<String> passwordHash = ArgumentCaptor.forClass(String.class);
        verify(repository).updatePassword(
                eq(accountId),
                passwordHash.capture(),
                eq("bcrypt"),
                eq("admin@example.com"),
                any()
        );
        assertThat(passwordHash.getValue()).isNotEqualTo("Password2!");
        assertThat(new BCryptPasswordEncoder().matches("Password2!", passwordHash.getValue())).isTrue();
    }

    @Test
    void search_rejectsNonAdminCaller() {
        assertThatThrownBy(() -> service.search(null, "ALL", null, 0, 20, nonAdmin))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Component.Permission.Denied");
    }

    @Test
    void search_normalizesPagingAndStatusAndCapsSize() {
        when(repository.findPage(eq("admin"), eq("ACTIVE"), eq(List.of()), eq(0), eq(100)))
                .thenReturn(List.of());
        when(repository.count(eq("admin"), eq("ACTIVE"), eq(List.of()))).thenReturn(0L);

        PageResult<UserAccountAdminView> result = service.search(" admin ", "active", null, -2, 500, admin);

        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(100);
        verify(repository).findPage(eq("admin"), eq("ACTIVE"), eq(List.of()), eq(0), eq(100));
    }

    @Test
    void search_normalizesRoleIdsByRemovingNullAndDuplicates() {
        when(repository.findPage(isNull(), eq("ALL"), eq(List.of(userRoleId, adminRoleId)), eq(20), eq(20)))
                .thenReturn(List.of());
        when(repository.count(isNull(), eq("ALL"), eq(List.of(userRoleId, adminRoleId)))).thenReturn(0L);

        service.search(null, null, Arrays.asList(userRoleId, null, userRoleId, adminRoleId), 1, 20, admin);

        verify(repository).findPage(isNull(), eq("ALL"), eq(List.of(userRoleId, adminRoleId)), eq(20), eq(20));
        verify(repository).count(isNull(), eq("ALL"), eq(List.of(userRoleId, adminRoleId)));
    }

    @Test
    void search_rejectsInvalidStatus() {
        assertThatThrownBy(() -> service.search(null, "deleted", null, 0, 20, admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.UserAccounts.Status.Invalid");

        verify(repository, never()).findPage(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void roles_rejectsNonAdminCaller() {
        assertThatThrownBy(() -> service.roles(nonAdmin))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Component.Permission.Denied");
    }

    private RoleOption role(UUID roleId, String roleName) {
        return RoleOption.builder().roleId(roleId).roleName(roleName).build();
    }

    private UserAccountAdminView account(UUID accountId, UUID memberKey, String username, String fullname,
                                         UUID roleId, String roleName, boolean active) {
        return UserAccountAdminView.builder()
                .accountId(accountId)
                .memberKey(memberKey)
                .username(username)
                .fullname(fullname)
                .email(username + "@example.com")
                .roleId(roleId)
                .roleName(roleName)
                .teamId(null)
                .teamName(null)
                .active(active)
                .build();
    }
}
