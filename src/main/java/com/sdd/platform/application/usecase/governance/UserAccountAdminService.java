package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.UserAccountAdminRepositoryPort;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.RoleOption;
import com.sdd.platform.domain.model.UserAccountAdminView;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserAccountAdminService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String PASSWORD_ALGO = "bcrypt";

    private final UserAccountAdminRepositoryPort repository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserAccountAdminService(UserAccountAdminRepositoryPort repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResult<UserAccountAdminView> search(
            String keyword,
            String status,
            List<UUID> roleIds,
            int page,
            int size,
            AppUser caller
    ) {
        requireAdmin(caller);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = normalizePageSize(size);
        String normalizedKeyword = trimToNull(keyword);
        String normalizedStatus = normalizeStatusFilter(status);
        List<UUID> normalizedRoleIds = normalizeRoleIds(roleIds);

        int offset = normalizedPage * normalizedSize;
        List<UserAccountAdminView> items =
                repository.findPage(normalizedKeyword, normalizedStatus, normalizedRoleIds, offset, normalizedSize);
        long totalElements = repository.count(normalizedKeyword, normalizedStatus, normalizedRoleIds);
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / normalizedSize);
        return new PageResult<>(items, normalizedPage, normalizedSize, totalElements, totalPages);
    }

    @Transactional(readOnly = true)
    public UserAccountAdminView get(UUID accountId, AppUser caller) {
        requireAdmin(caller);
        return findExisting(accountId);
    }

    @Transactional(readOnly = true)
    public List<RoleOption> roles(AppUser caller) {
        requireAdmin(caller);
        return repository.findRoles();
    }

    @Transactional
    public UserAccountAdminView create(
            String username,
            String fullname,
            String email,
            String password,
            String confirmPassword,
            UUID roleId,
            Boolean active,
            AppUser caller
    ) {
        requireAdmin(caller);
        String normalizedUsername = normalizeUsername(username);
        String normalizedFullname = normalizeFullname(fullname, normalizedUsername);
        String normalizedEmail = normalizeEmail(email);
        validatePassword(password, confirmPassword);
        validateRole(roleId);
        if (repository.existsUsername(normalizedUsername, null)) {
            throw new BusinessRuleException("Pages.UserAccounts.Username.Duplicate");
        }

        UUID accountId = UUID.randomUUID();
        UUID memberKey = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String actor = resolveActor(caller);
        String passwordHash = passwordEncoder.encode(password);

        repository.insertMember(memberKey, roleId, normalizedUsername, actor, now);
        repository.insertAccount(
                accountId,
                memberKey,
                normalizedUsername,
                normalizedFullname,
                normalizedEmail,
                passwordHash,
                PASSWORD_ALGO,
                Boolean.TRUE.equals(active),
                actor,
                now
        );
        return findExisting(accountId);
    }

    @Transactional
    public UserAccountAdminView update(
            UUID accountId,
            String fullname,
            String email,
            UUID roleId,
            Boolean active,
            AppUser caller
    ) {
        requireAdmin(caller);
        UserAccountAdminView existing = findExisting(accountId);
        RoleOption nextRole = validateRole(roleId);
        boolean nextActive = Boolean.TRUE.equals(active);
        if (existing.isActive() && isAdminRole(existing)
                && (!nextActive || !"ADMIN".equalsIgnoreCase(nextRole.getRoleName()))) {
            assertNotLastActiveAdmin(accountId);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String actor = resolveActor(caller);
        int accountRows = repository.updateAccount(
                accountId,
                normalizeFullname(fullname, existing.getUsername()),
                normalizeEmail(email),
                nextActive,
                actor,
                now
        );
        int memberRows = repository.updateMemberRole(existing.getMemberKey(), roleId, actor, now);
        if (accountRows == 0 || memberRows == 0) {
            throw new NotFoundException("Pages.UserAccounts.NotFound");
        }
        return findExisting(accountId);
    }

    @Transactional
    public UserAccountAdminView activate(UUID accountId, AppUser caller) {
        requireAdmin(caller);
        findExisting(accountId);
        updateActive(accountId, true, caller);
        return findExisting(accountId);
    }

    @Transactional
    public UserAccountAdminView deactivate(UUID accountId, AppUser caller) {
        requireAdmin(caller);
        UserAccountAdminView existing = findExisting(accountId);
        if (existing.isActive() && isAdminRole(existing)) {
            assertNotLastActiveAdmin(accountId);
        }
        updateActive(accountId, false, caller);
        return findExisting(accountId);
    }

    @Transactional
    public UserAccountAdminView resetPassword(UUID accountId, String password, String confirmPassword, AppUser caller) {
        requireAdmin(caller);
        findExisting(accountId);
        validatePassword(password, confirmPassword);
        int rows = repository.updatePassword(
                accountId,
                passwordEncoder.encode(password),
                PASSWORD_ALGO,
                resolveActor(caller),
                OffsetDateTime.now()
        );
        if (rows == 0) {
            throw new NotFoundException("Pages.UserAccounts.NotFound");
        }
        return findExisting(accountId);
    }

    private void updateActive(UUID accountId, boolean active, AppUser caller) {
        int rows = repository.updateActive(accountId, active, resolveActor(caller), OffsetDateTime.now());
        if (rows == 0) {
            throw new NotFoundException("Pages.UserAccounts.NotFound");
        }
    }

    private UserAccountAdminView findExisting(UUID accountId) {
        return repository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Pages.UserAccounts.NotFound"));
    }

    private List<UUID> normalizeRoleIds(List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        roleIds.forEach(roleId -> {
            if (roleId != null) {
            validateRole(roleId);
            }
        });
        return roleIds.stream().filter(roleId -> roleId != null).distinct().toList();
    }

    private RoleOption validateRole(UUID roleId) {
        if (roleId == null) {
            throw new BusinessRuleException("Pages.UserAccounts.Role.Required");
        }
        return repository.findRoleById(roleId)
                .orElseThrow(() -> new BusinessRuleException("Pages.UserAccounts.Role.Invalid"));
    }

    private void validatePassword(String password, String confirmPassword) {
        if (password == null || password.isBlank()) {
            throw new BusinessRuleException("Pages.UserAccounts.Password.Required");
        }
        if (password.length() < 8) {
            throw new BusinessRuleException("Pages.UserAccounts.Password.MinLength");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessRuleException("Pages.UserAccounts.Password.Pattern");
        }
        if (!password.equals(confirmPassword)) {
            throw new BusinessRuleException("Pages.UserAccounts.Password.Mismatch");
        }
    }

    private void assertNotLastActiveAdmin(UUID accountId) {
        if (repository.countActiveAdminsExcluding(accountId) == 0) {
            throw new BusinessRuleException("Pages.UserAccounts.LastAdmin");
        }
    }

    private boolean isAdminRole(UserAccountAdminView account) {
        return "ADMIN".equalsIgnoreCase(account.getRoleName());
    }

    private String normalizeUsername(String username) {
        String normalized = trimToNull(username);
        if (normalized == null) {
            throw new BusinessRuleException("Pages.UserAccounts.Username.Required");
        }
        if (normalized.length() > 100) {
            throw new BusinessRuleException("Pages.UserAccounts.Username.MaxLength");
        }
        return normalized;
    }

    private String normalizeFullname(String fullname, String fallback) {
        String normalized = trimToNull(fullname);
        if (normalized == null) {
            normalized = fallback;
        }
        if (normalized.length() > 250) {
            throw new BusinessRuleException("Pages.UserAccounts.Fullname.MaxLength");
        }
        return normalized;
    }

    private String normalizeEmail(String email) {
        String normalized = trimToNull(email);
        if (normalized != null && normalized.length() > 255) {
            throw new BusinessRuleException("Pages.UserAccounts.Email.MaxLength");
        }
        return normalized;
    }

    private String normalizeStatusFilter(String status) {
        String normalized = status == null || status.isBlank() ? "ALL" : status.trim().toUpperCase(Locale.ROOT);
        if (!"ALL".equals(normalized) && !"ACTIVE".equals(normalized) && !"INACTIVE".equals(normalized)) {
            throw new BusinessRuleException("Pages.UserAccounts.Status.Invalid");
        }
        return normalized;
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void requireAdmin(AppUser caller) {
        if (caller == null || caller.getRole() != AppUser.Role.ADMIN) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    private String resolveActor(AppUser caller) {
        if (caller == null) {
            return "SYSTEM";
        }
        if (caller.getEmail() != null && !caller.getEmail().isBlank()) {
            return caller.getEmail().trim();
        }
        if (caller.getDisplayName() != null && !caller.getDisplayName().isBlank()) {
            return caller.getDisplayName().trim();
        }
        return "SYSTEM";
    }
}
