package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.exception.OptimisticLockingException;
import com.sdd.platform.application.port.out.persistence.TeamRepositoryPort;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.Team;
import com.sdd.platform.domain.model.TeamMember;
import com.sdd.platform.domain.model.TeamMemberOption;
import com.sdd.platform.domain.model.TeamRoleOption;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TeamService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final TeamRepositoryPort repository;

    public TeamService(TeamRepositoryPort repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResult<Team> search(String keyword, String status, int page, int size, AppUser caller) {
        requireAdmin(caller);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = normalizePageSize(size);
        String normalizedKeyword = trimToNull(keyword);
        String normalizedStatus = normalizeStatusFilter(status);
        int offset = normalizedPage * normalizedSize;

        List<Team> items = repository.findPage(normalizedKeyword, normalizedStatus, offset, normalizedSize);
        long totalElements = repository.count(normalizedKeyword, normalizedStatus);
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / normalizedSize);
        return new PageResult<>(items, normalizedPage, normalizedSize, totalElements, totalPages);
    }

    @Transactional(readOnly = true)
    public Team get(UUID teamId, AppUser caller) {
        requireAdmin(caller);
        return repository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Pages.Team.NotFound"));
    }

    @Transactional
    public Team create(String teamCode, String teamName, String description, AppUser caller) {
        requireAdmin(caller);
        String normalizedCode = normalizeRequired(teamCode, 50, "Pages.Team.Code.Required");
        String normalizedName = normalizeRequired(teamName, 255, "Pages.Team.Name.Required");
        String normalizedDescription = normalizeOptional(description, 500, "Pages.Team.Description.MaxLength");
        ensureUniqueCode(normalizedCode, null);

        String actor = resolveActor(caller);
        OffsetDateTime now = OffsetDateTime.now();
        Team team = Team.builder()
                .teamId(UUID.randomUUID())
                .teamCode(normalizedCode)
                .teamName(normalizedName)
                .description(normalizedDescription)
                .status(Team.TeamStatus.ACTIVE)
                .createdAt(now)
                .createdBy(actor)
                .updatedAt(now)
                .updatedBy(actor)
                .version(0L)
                .memberCount(0L)
                .build();
        return repository.insert(team);
    }

    @Transactional
    public Team update(
            UUID teamId,
            String teamCode,
            String teamName,
            String description,
            String status,
            long version,
            AppUser caller
    ) {
        requireAdmin(caller);
        Team existing = repository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Pages.Team.NotFound"));
        ensureEditable(existing);

        String normalizedCode = normalizeRequired(teamCode, 50, "Pages.Team.Code.Required");
        String normalizedName = normalizeRequired(teamName, 255, "Pages.Team.Name.Required");
        String normalizedDescription = normalizeOptional(description, 500, "Pages.Team.Description.MaxLength");
        Team.TeamStatus normalizedStatus = existing.getStatus();
        String normalizedStatusInput = trimToNull(status);
        if (normalizedStatusInput != null && !"ACTIVE".equalsIgnoreCase(normalizedStatusInput)) {
            throw new BusinessRuleException("Pages.Team.Status.Invalid");
        }
        ensureUniqueCode(normalizedCode, teamId);

        String actor = resolveActor(caller);
        existing.setTeamCode(normalizedCode);
        existing.setTeamName(normalizedName);
        existing.setDescription(normalizedDescription);
        existing.setStatus(normalizedStatus);
        existing.setVersion(version);
        existing.setUpdatedAt(OffsetDateTime.now());
        existing.setUpdatedBy(actor);

        int affected = repository.update(existing);
        if (affected == 0) {
            throw new OptimisticLockingException("Pages.Team.Conflict.Version");
        }
        return repository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Pages.Team.NotFound"));
    }

    @Transactional
    public Team softDelete(UUID teamId, long version, AppUser caller) {
        requireAdmin(caller);
        Team existing = repository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Pages.Team.NotFound"));
        if (existing.isDeleted()) {
            throw new BusinessRuleException("Pages.Team.AlreadyDeleted");
        }

        String actor = resolveActor(caller);
        OffsetDateTime now = OffsetDateTime.now();
        int affected = repository.softDelete(teamId, version, actor, now, actor, now);
        if (affected == 0) {
            throw new OptimisticLockingException("Pages.Team.Conflict.Version");
        }
        return repository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Pages.Team.NotFound"));
    }

    @Transactional(readOnly = true)
    public List<TeamMember> listMembers(UUID teamId, AppUser caller) {
        requireAdmin(caller);
        get(teamId, caller);
        return repository.findMembers(teamId, "ACTIVE");
    }

    @Transactional
    public TeamMember addMember(UUID teamId, UUID memberKey, UUID roleId, AppUser caller) {
        requireAdmin(caller);
        Team team = get(teamId, caller);
        ensureActive(team);
        validateMemberAndRole(memberKey, roleId);

        if (!repository.existsActiveMember(memberKey)) {
            throw new NotFoundException("Pages.Team.Member.NotFound");
        }
        if (!repository.existsRole(roleId)) {
            throw new NotFoundException("Pages.Team.Role.NotFound");
        }
        if (repository.existsActiveMembership(teamId, memberKey, null)) {
            throw new BusinessRuleException("Pages.Team.Member.Duplicate");
        }

        String actor = resolveActor(caller);
        OffsetDateTime now = OffsetDateTime.now();
        TeamMember teamMember = TeamMember.builder()
                .teamMemberId(UUID.randomUUID())
                .teamId(teamId)
                .memberKey(memberKey)
                .roleId(roleId)
                .status(TeamMember.TeamMemberStatus.ACTIVE)
                .joinedAt(now)
                .createdAt(now)
                .createdBy(actor)
                .updatedAt(now)
                .updatedBy(actor)
                .version(0L)
                .build();
        return repository.insertMember(teamMember);
    }

    @Transactional
    public TeamMember updateMemberRole(UUID teamId, UUID teamMemberId, UUID roleId, long version, AppUser caller) {
        requireAdmin(caller);
        Team team = get(teamId, caller);
        ensureActive(team);
        if (!repository.existsRole(roleId)) {
            throw new NotFoundException("Pages.Team.Role.NotFound");
        }
        TeamMember existing = repository.findMemberById(teamMemberId)
                .orElseThrow(() -> new NotFoundException("Pages.Team.Member.NotFound"));
        ensureActiveMembership(existing, teamId);

        String actor = resolveActor(caller);
        existing.setRoleId(roleId);
        existing.setVersion(version);
        existing.setUpdatedAt(OffsetDateTime.now());
        existing.setUpdatedBy(actor);

        int affected = repository.updateMemberRole(existing);
        if (affected == 0) {
            throw new OptimisticLockingException("Pages.Team.Conflict.Version");
        }
        return repository.findMemberById(teamMemberId)
                .orElseThrow(() -> new NotFoundException("Pages.Team.Member.NotFound"));
    }

    @Transactional
    public TeamMember removeMember(UUID teamId, UUID teamMemberId, long version, AppUser caller) {
        requireAdmin(caller);
        Team team = get(teamId, caller);
        ensureActive(team);
        TeamMember existing = repository.findMemberById(teamMemberId)
                .orElseThrow(() -> new NotFoundException("Pages.Team.Member.NotFound"));
        ensureActiveMembership(existing, teamId);

        String actor = resolveActor(caller);
        OffsetDateTime now = OffsetDateTime.now();
        int affected = repository.removeMember(teamMemberId, version, actor, now, actor, now);
        if (affected == 0) {
            throw new OptimisticLockingException("Pages.Team.Conflict.Version");
        }
        return repository.findMemberById(teamMemberId)
                .orElseThrow(() -> new NotFoundException("Pages.Team.Member.NotFound"));
    }

    @Transactional(readOnly = true)
    public List<TeamMemberOption> listMemberOptions(AppUser caller) {
        requireAdmin(caller);
        return repository.findActiveMemberOptions(null);
    }

    @Transactional(readOnly = true)
    public List<TeamRoleOption> listRoleOptions(AppUser caller) {
        requireAdmin(caller);
        return repository.findRoleOptions();
    }

    private void validateMemberAndRole(UUID memberKey, UUID roleId) {
        if (memberKey == null) {
            throw new BusinessRuleException("Pages.Team.Member.Required");
        }
        if (roleId == null) {
            throw new BusinessRuleException("Pages.Team.Role.Required");
        }
    }

    private void ensureActive(Team team) {
        if (team.isDeleted()) {
            throw new BusinessRuleException("Pages.Team.NotActive");
        }
    }

    private void ensureEditable(Team team) {
        if (team.isDeleted()) {
            throw new BusinessRuleException("Pages.Team.Deleted.CannotEdit");
        }
    }

    private void ensureActiveMembership(TeamMember teamMember, UUID teamId) {
        if (!teamId.equals(teamMember.getTeamId()) || teamMember.isDeleted()) {
            throw new NotFoundException("Pages.Team.Member.NotFound");
        }
    }

    private void ensureUniqueCode(String teamCode, UUID excludeTeamId) {
        if (repository.existsActiveCode(teamCode, excludeTeamId)) {
            throw new BusinessRuleException("Pages.Team.Code.Duplicate");
        }
    }

    private String normalizeRequired(String value, int maxLength, String emptyMessageKey) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessRuleException(emptyMessageKey);
        }
        if (normalized.length() > maxLength) {
            throw new BusinessRuleException(maxLength == 50
                    ? "Pages.Team.Code.MaxLength"
                    : "Pages.Team.Name.MaxLength");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength, String messageKey) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new BusinessRuleException(messageKey);
        }
        return normalized;
    }

    private String normalizeStatusFilter(String status) {
        String normalized = trimToNull(status);
        if (normalized == null) {
            return "ACTIVE";
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!"ALL".equals(upper) && !"ACTIVE".equals(upper) && !"DELETED".equals(upper)) {
            throw new BusinessRuleException("Pages.Team.Status.Invalid");
        }
        return upper;
    }

    private void requireAdmin(AppUser caller) {
        if (caller == null || caller.getRole() != AppUser.Role.ADMIN) {
            throw new ForbiddenException("Pages.Team.Error.Forbidden");
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
}
