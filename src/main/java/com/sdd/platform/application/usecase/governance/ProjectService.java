package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.ProjectRepositoryPort;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.Project;
import com.sdd.platform.domain.model.ProjectTeamAssignment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProjectService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int PROJECT_TYPE_MAX_LENGTH = 100;

    private final ProjectRepositoryPort repository;

    public ProjectService(ProjectRepositoryPort repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResult<Project> search(UUID customerId, String keyword, String status, int page, int size, AppUser caller) {
        requireAdmin(caller);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = normalizePageSize(size);
        String normalizedKeyword = trimToNull(keyword);
        String normalizedStatus = normalizeStatusFilter(status);
        int offset = normalizedPage * normalizedSize;
        List<Project> items = repository.findPage(normalizedKeyword, customerId, normalizedStatus, offset, normalizedSize);
        long total = repository.count(normalizedKeyword, customerId, normalizedStatus);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / normalizedSize);
        return new PageResult<>(items, normalizedPage, normalizedSize, total, totalPages);
    }

    @Transactional(readOnly = true)
    public Project get(UUID projectId, AppUser caller) {
        requireAdmin(caller);
        Project project = repository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Pages.Project.NotFound"));
        if (project.isDeleted()) {
            throw new NotFoundException("Pages.Project.NotFound");
        }
        project.setTeamAssignments(repository.findActiveTeamAssignments(projectId));
        return project;
    }

    @Transactional
    public Project create(
            UUID customerId,
            String projectAlias,
            String projectType,
            String riskLevel,
            List<UUID> teamIds,
            AppUser caller
    ) {
        requireAdmin(caller);
        UUID normalizedCustomerId = requireCustomerId(customerId);
        ensureActiveCustomer(normalizedCustomerId);
        String normalizedAlias = normalizeRequired(projectAlias, 255, "Pages.Project.Alias.Required", "Pages.Project.Alias.MaxLength");
        String normalizedProjectType = normalizeOptional(projectType, PROJECT_TYPE_MAX_LENGTH, "Pages.Project.ProjectType.MaxLength");
        Project.RiskLevel normalizedRiskLevel = normalizeRiskLevel(riskLevel);
        List<UUID> normalizedTeamIds = normalizeTeamIds(teamIds);
        ensureUniqueAlias(normalizedCustomerId, normalizedAlias, null);
        validateTeamIds(normalizedTeamIds);

        String actor = resolveActor(caller);
        OffsetDateTime now = OffsetDateTime.now();
        Project project = Project.builder()
                .projectId(UUID.randomUUID())
                .customerId(normalizedCustomerId)
                .projectAlias(normalizedAlias)
                .projectType(normalizedProjectType)
                .riskLevel(normalizedRiskLevel)
                .status(Project.ProjectStatus.ACTIVE)
                .deleteFlag(false)
                .createdAt(now)
                .createdBy(actor)
                .updatedAt(now)
                .updatedBy(actor)
                .build();
        repository.insert(project);
        syncTeamAssignments(project.getProjectId(), normalizedTeamIds, actor, now);
        return get(project.getProjectId(), caller);
    }

    @Transactional
    public Project update(
            UUID projectId,
            UUID customerId,
            String projectAlias,
            String projectType,
            String riskLevel,
            List<UUID> teamIds,
            AppUser caller
    ) {
        requireAdmin(caller);
        Project existing = repository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Pages.Project.NotFound"));
        if (existing.isDeleted()) {
            throw new NotFoundException("Pages.Project.NotFound");
        }

        UUID normalizedCustomerId = requireCustomerId(customerId);
        ensureActiveCustomer(normalizedCustomerId);
        String normalizedAlias = normalizeRequired(projectAlias, 255, "Pages.Project.Alias.Required", "Pages.Project.Alias.MaxLength");
        String normalizedProjectType = normalizeOptional(projectType, PROJECT_TYPE_MAX_LENGTH, "Pages.Project.ProjectType.MaxLength");
        Project.RiskLevel normalizedRiskLevel = normalizeRiskLevel(riskLevel);
        List<UUID> normalizedTeamIds = normalizeTeamIds(teamIds);
        ensureUniqueAlias(normalizedCustomerId, normalizedAlias, projectId);
        validateTeamIds(normalizedTeamIds);

        existing.setCustomerId(normalizedCustomerId);
        existing.setProjectAlias(normalizedAlias);
        existing.setProjectType(normalizedProjectType);
        existing.setRiskLevel(normalizedRiskLevel);
        existing.setUpdatedAt(OffsetDateTime.now());
        existing.setUpdatedBy(resolveActor(caller));

        int affected = repository.update(existing);
        if (affected == 0) {
            throw new NotFoundException("Pages.Project.NotFound");
        }
        syncTeamAssignments(projectId, normalizedTeamIds, existing.getUpdatedBy(), existing.getUpdatedAt());
        return get(projectId, caller);
    }

    @Transactional
    public Project softDelete(UUID projectId, AppUser caller) {
        requireAdmin(caller);
        Project existing = repository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Pages.Project.NotFound"));
        if (existing.isDeleted()) {
            throw new NotFoundException("Pages.Project.NotFound");
        }
        String actor = resolveActor(caller);
        OffsetDateTime now = OffsetDateTime.now();
        int affected = repository.softDelete(projectId, actor, now, actor, now);
        if (affected == 0) {
            throw new NotFoundException("Pages.Project.NotFound");
        }
        return repository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Pages.Project.NotFound"));
    }

    private void syncTeamAssignments(UUID projectId, List<UUID> teamIds, String actor, OffsetDateTime now) {
        List<ProjectTeamAssignment> currentAssignments = repository.findActiveTeamAssignments(projectId);
        Set<UUID> currentTeamIds = currentAssignments.stream().map(ProjectTeamAssignment::getTeamId).collect(LinkedHashSet::new, Set::add, Set::addAll);
        Set<UUID> desiredTeamIds = new LinkedHashSet<>(teamIds);

        for (ProjectTeamAssignment assignment : currentAssignments) {
            if (!desiredTeamIds.contains(assignment.getTeamId())) {
                repository.deactivateTeamAssignment(projectId, assignment.getTeamId(), actor, now, actor, now);
            }
        }

        for (UUID teamId : desiredTeamIds) {
            if (!currentTeamIds.contains(teamId)) {
                int reactivated = repository.reactivateTeamAssignment(projectId, teamId, actor, now);
                if (reactivated == 0) {
                    repository.insertTeamAssignment(ProjectTeamAssignment.builder()
                            .projectTeamId(UUID.randomUUID())
                            .projectId(projectId)
                            .teamId(teamId)
                            .status(Project.ProjectStatus.ACTIVE)
                            .createdAt(now)
                            .createdBy(actor)
                            .updatedAt(now)
                            .updatedBy(actor)
                            .build());
                }
            }
        }
    }

    private void ensureActiveCustomer(UUID customerId) {
        if (!repository.existsActiveCustomer(customerId)) {
            throw new BusinessRuleException("Pages.Project.Customer.Unavailable");
        }
    }

    private void ensureUniqueAlias(UUID customerId, String alias, UUID excludeProjectId) {
        if (repository.existsActiveAlias(customerId, alias, excludeProjectId)) {
            throw new BusinessRuleException("Pages.Project.Alias.Duplicate");
        }
    }

    private void validateTeamIds(List<UUID> teamIds) {
        if (teamIds.isEmpty()) {
            return;
        }
        List<UUID> activeTeamIds = repository.findActiveTeamIdsByIds(teamIds);
        if (activeTeamIds.size() != teamIds.size()) {
            throw new BusinessRuleException("Pages.Project.Team.Invalid");
        }
    }

    private UUID requireCustomerId(UUID customerId) {
        if (customerId == null) {
            throw new BusinessRuleException("Pages.Project.Customer.Required");
        }
        return customerId;
    }

    private List<UUID> normalizeTeamIds(List<UUID> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>();
        for (UUID teamId : teamIds) {
            if (teamId == null) {
                throw new BusinessRuleException("Pages.Project.Team.Invalid");
            }
            uniqueIds.add(teamId);
        }
        return new ArrayList<>(uniqueIds);
    }

    private Project.RiskLevel normalizeRiskLevel(String riskLevel) {
        String normalized = normalizeSeverityValue(riskLevel, "Pages.Project.RiskLevel.Invalid");
        return normalized == null ? Project.RiskLevel.MEDIUM : Project.RiskLevel.valueOf(normalized);
    }

    private String normalizeOptional(String value, int maxLength, String maxLengthMessageKey) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new BusinessRuleException(maxLengthMessageKey);
        }
        return normalized;
    }

    private String normalizeSeverityValue(String value, String invalidMessageKey) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        try {
            Project.RiskLevel.valueOf(upper);
            return upper;
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(invalidMessageKey);
        }
    }

    private String normalizeStatusFilter(String status) {
        String normalized = status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase(Locale.ROOT);
        if (!"ALL".equals(normalized) && !"ACTIVE".equals(normalized) && !"DELETED".equals(normalized)) {
            throw new BusinessRuleException("Pages.Project.Status.Invalid");
        }
        return normalized;
    }

    private String normalizeRequired(String value, int maxLength, String requiredMessageKey, String maxLengthMessageKey) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessRuleException(requiredMessageKey);
        }
        if (normalized.length() > maxLength) {
            throw new BusinessRuleException(maxLengthMessageKey);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private void requireAdmin(AppUser caller) {
        if (caller == null || caller.getRole() != AppUser.Role.ADMIN) {
            throw new ForbiddenException("Pages.Project.Error.Forbidden");
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
