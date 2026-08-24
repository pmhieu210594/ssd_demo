package com.sdd.platform.application.usecase.quality;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.exception.OptimisticLockingException;
import com.sdd.platform.application.port.out.persistence.ScoreThresholdConfigRepositoryPort;
import com.sdd.platform.application.usecase.governance.AdminAuditLogService;
import com.sdd.platform.application.usecase.quality.ScoreThresholdConfigModels.ScoreThreshold;
import com.sdd.platform.application.usecase.quality.ScoreThresholdConfigModels.UpsertScoreThreshold;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AppUser;

/**
 * Single source of truth for score-threshold band configuration (HD-THRESHOLD-CONFIG-1).
 * Replaces the previously hardcoded {@code ScoreBand} enum, {@code VALID_SCORE_BANDS} set, and
 * {@code toDisplayBand} switch. The active band list is cached in-memory and refreshed on every
 * {@link #save(List, AppUser)} call so the per-scoring-event {@link #lookupBand(BigDecimal)} hot
 * path (OI-THRESHOLD-CONFIG-8) avoids a DB round-trip on every lookup.
 */
@Service
public class ScoreThresholdConfigService {

    private static final String MODULE = "SCORE_THRESHOLD";
    private static final String ENTITY_TYPE = "SCORE_THRESHOLD";

    private final ScoreThresholdConfigRepositoryPort repository;
    private final AdminAuditLogService adminAuditLogService;

    private volatile List<ScoreThreshold> cachedActive;

    public ScoreThresholdConfigService(ScoreThresholdConfigRepositoryPort repository,
            AdminAuditLogService adminAuditLogService) {
        this.repository = repository;
        this.adminAuditLogService = adminAuditLogService;
    }

    @Transactional(readOnly = true)
    public List<ScoreThreshold> list(AppUser caller) {
        requireAdmin(caller);
        return repository.findActiveOrderedByMinScore();
    }

    @Transactional
    public List<ScoreThreshold> save(List<UpsertScoreThreshold> requestRows, AppUser caller) {
        requireAdmin(caller);
        try {
            for (UpsertScoreThreshold row : requestRows) {
                ScoreThresholdConfigModels.validateRow(row);
            }
            ScoreThresholdConfigModels.validateActiveSet(requestRows);

            List<ScoreThreshold> currentActive = repository.findActiveOrderedByMinScore();
            Set<UUID> matchedIds = requestRows.stream()
                    .map(UpsertScoreThreshold::id)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());

            for (UpsertScoreThreshold row : requestRows) {
                if (row.id() != null && currentActive.stream().noneMatch(a -> a.id().equals(row.id()))) {
                    throw new NotFoundException("Pages.ThresholdConfig.NotFound");
                }
            }

            OffsetDateTime now = OffsetDateTime.now();
            String actor = resolveActor(caller);

            // Soft delete
            List<ScoreThreshold> toSoftDelete = currentActive.stream()
                    .filter(active -> !matchedIds.contains(active.id()))
                    .toList();
            for (ScoreThreshold removed : toSoftDelete) {
                int affected = repository.softDelete(removed.id(), actor, now);
                if (affected == 0) {
                    throw new OptimisticLockingException("Pages.ThresholdConfig.Conflict.Version");
                }
                adminAuditLogService.logDelete(caller, MODULE, ENTITY_TYPE, removed.id().toString(), removed);
            }

            List<UpsertScoreThreshold> insertList = new ArrayList<>();
            List<UpsertScoreThreshold> updateList = new ArrayList<>();
            for (UpsertScoreThreshold row : requestRows) {
                if (row.id() == null) {
                    insertList.add(row);
                } else {
                    updateList.add(row);
                }
            }

            // update batch
            repository.updateBatch(updateList, actor, now);
            // audit log - update
            List<UpsertScoreThreshold> newList = updateList.stream()
                .map(oldItem -> new UpsertScoreThreshold(
                    oldItem.id(),
                    oldItem.code(),
                    oldItem.label(),
                    oldItem.minScore(),
                    oldItem.maxScore(),
                    oldItem.color(),
                    oldItem.createdAt(),
                    oldItem.createdBy(),
                    now,
                    actor
                ))
                .collect(Collectors.toList());
            for (int i = 0; i < newList.size(); i++) {
                final int index = i;
                ScoreThreshold before = currentActive.stream()
                            .filter(a -> a.id().equals(newList.get(index).id()))
                            .findFirst()
                            .orElseThrow(() -> new NotFoundException("Pages.ThresholdConfig.NotFound"));

                adminAuditLogService.logUpdate(caller, MODULE, ENTITY_TYPE, newList.get(index).id().toString(),
                            adminAuditLogService.snapshot(before), newList.get(index));
            }

            //insert
            for (UpsertScoreThreshold row : insertList) {
                ScoreThreshold created = repository.insert(row, actor, now);
                adminAuditLogService.logCreate(caller, MODULE, ENTITY_TYPE, created.id().toString(), created);
            }

            List<ScoreThreshold> refreshed = repository.findActiveOrderedByMinScore();
            cachedActive = refreshed;
            return refreshed;
        } catch (RuntimeException ex) {
            adminAuditLogService.logCrudFailure(caller, MODULE, ENTITY_TYPE, null, "SAVE", ex.getMessage());
            throw ex;
        }
    }

    public ScoreThreshold lookupBand(BigDecimal score) {
        int normalized = normalizeScore(score);
        return ScoreThresholdConfigModels.lookupBand(activeOrdered(), normalized);
    }

    public Set<String> getActiveCodes() {
        return activeOrdered().stream().map(ScoreThreshold::code).collect(Collectors.toSet());
    }

    public String displayNameForCode(String code) {
        if (code == null || code.isBlank()) {
            return code;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return activeOrdered().stream()
                .filter(band -> band.code().equals(normalized))
                .map(ScoreThreshold::label)
                .findFirst()
                .orElse(code);
    }

    private List<ScoreThreshold> activeOrdered() {
        List<ScoreThreshold> snapshot = cachedActive;
        if (snapshot == null) {
            synchronized (this) {
                snapshot = cachedActive;
                if (snapshot == null) {
                    snapshot = repository.findActiveOrderedByMinScore().stream()
                            .sorted(Comparator.comparingInt(ScoreThreshold::minScore))
                            .toList();
                    cachedActive = snapshot;
                }
            }
        }
        return snapshot;
    }

    private int normalizeScore(BigDecimal score) {
        if (score == null) {
            return 0;
        }
        int value = score.setScale(0, RoundingMode.HALF_UP).intValue();
        if (value < ScoreThresholdConfigModels.MIN_SCORE_BOUND) {
            return ScoreThresholdConfigModels.MIN_SCORE_BOUND;
        }
        if (value > ScoreThresholdConfigModels.MAX_SCORE_BOUND) {
            return ScoreThresholdConfigModels.MAX_SCORE_BOUND;
        }
        return value;
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
