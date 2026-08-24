package com.sdd.platform.application.usecase.quality;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.ScoreThresholdConfigRepositoryPort;
import com.sdd.platform.application.usecase.governance.AdminAuditLogService;
import com.sdd.platform.application.usecase.quality.ScoreThresholdConfigModels.ScoreThreshold;
import com.sdd.platform.application.usecase.quality.ScoreThresholdConfigModels.UpsertScoreThreshold;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.model.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreThresholdConfigServiceTest {

    private static final UUID EXCELLENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GOOD_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final OffsetDateTime FIXED_NOW = OffsetDateTime.parse("2026-01-01T00:00:00+00:00");

    @Mock
    private ScoreThresholdConfigRepositoryPort repository;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    private ScoreThresholdConfigService service;

    @BeforeEach
    void setUp() {
        service = new ScoreThresholdConfigService(repository, adminAuditLogService);
    }

    @Test
    void list_rejectsNonAdmin() {
        assertThatThrownBy(() -> service.list(viewerUser())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void list_returnsActiveBands() {
        List<ScoreThreshold> active = List.of(defaultExcellent());
        when(repository.findActiveOrderedByMinScore()).thenReturn(active);

        assertThat(service.list(adminUser())).containsExactly(defaultExcellent());
    }

    @Test
    void save_rejectsNonAdmin() {
        assertThatThrownBy(() -> service.save(List.of(), viewerUser())).isInstanceOf(ForbiddenException.class);
        verify(repository, never()).findActiveOrderedByMinScore();
    }

    @Test
    void save_rejectsGapInCoverage() {
        List<UpsertScoreThreshold> payload = List.of(
                row(null, "A", 0, 39),
                row(null, "B", 45, 100));

        assertThatThrownBy(() -> service.save(payload, adminUser()))
                .isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).findActiveOrderedByMinScore();
    }

    @Test
    void save_insertsUpdatesAndSoftDeletesInOneBatch() {
        OffsetDateTime now = OffsetDateTime.now();
        ScoreThreshold existingGood = new ScoreThreshold(GOOD_ID, "GOOD", "Good", 75, 89, "#0EA5E9", now, "SYSTEM", now, "SYSTEM");
        ScoreThreshold existingExcellent = defaultExcellent();
        when(repository.findActiveOrderedByMinScore())
                .thenReturn(List.of(existingExcellent, existingGood))
                .thenReturn(List.of(existingExcellent));
        when(repository.updateBatch(any(), any(), any())).thenReturn(new int[]{1});
        when(repository.softDelete(eq(GOOD_ID), any(), any())).thenReturn(1);
        when(repository.insert(any(), any(), any())).thenAnswer(inv -> {
            UpsertScoreThreshold r = inv.getArgument(0);
            String actor = inv.getArgument(1);
            
            return new ScoreThreshold(UUID.randomUUID(), r.code(), r.label(), r.minScore(), r.maxScore(), r.color(), now, actor, now, actor);
        });
        when(adminAuditLogService.snapshot(any())).thenReturn(Map.of());

        List<UpsertScoreThreshold> payload = List.of(
                row(EXCELLENT_ID, "EXCELLENT", 60, 100),
                row(null, "LOW", 0, 59));

        List<ScoreThreshold> result = service.save(payload, adminUser());

        assertThat(result).containsExactly(existingExcellent);
        verify(repository).updateBatch(any(), any(), any());
        verify(repository).softDelete(eq(GOOD_ID), any(), any());
        verify(repository).insert(any(), any(), any());
        verify(adminAuditLogService).logUpdate(any(), any(), any(), eq(EXCELLENT_ID.toString()), any(), any());
        verify(adminAuditLogService).logDelete(any(), any(), any(), eq(GOOD_ID.toString()), any());
        verify(adminAuditLogService).logCreate(any(), any(), any(), any(), any());
    }

    @Test
    void save_callsUpdateBatchWithMatchingRows() {
        ScoreThreshold existingExcellent = defaultExcellent();
        when(repository.findActiveOrderedByMinScore()).thenReturn(List.of(existingExcellent));
        when(repository.updateBatch(any(), any(), any())).thenReturn(new int[]{1});
        when(adminAuditLogService.snapshot(any())).thenReturn(Map.of());

        List<UpsertScoreThreshold> payload = List.of(row(EXCELLENT_ID, "EXCELLENT", 0, 100));

        service.save(payload, adminUser());

        verify(repository).updateBatch(
                argThat(updateList -> updateList.size() == 1 && updateList.get(0).id().equals(EXCELLENT_ID)),
                any(), any());
    }

    @Test
    void lookupBand_usesCacheAfterFirstAccessWithoutDbRoundTrip() {
        when(repository.findActiveOrderedByMinScore()).thenReturn(List.of(defaultExcellent()));

        assertThat(service.lookupBand(BigDecimal.valueOf(95)).code()).isEqualTo("EXCELLENT");
        assertThat(service.lookupBand(BigDecimal.valueOf(91)).code()).isEqualTo("EXCELLENT");

        verify(repository, org.mockito.Mockito.times(1)).findActiveOrderedByMinScore();
    }

    private static ScoreThreshold defaultExcellent() {
        return new ScoreThreshold(EXCELLENT_ID, "EXCELLENT", "Excellent", 90, 100, "#10B981", FIXED_NOW, "SYSTEM", FIXED_NOW, "SYSTEM");
    }

    private static UpsertScoreThreshold row(UUID id, String code, int min, int max) {
        OffsetDateTime now = OffsetDateTime.now();
        return new UpsertScoreThreshold(id, code, code + " label", min, max, "#10B981", now, "SYSTEM", now, "SYSTEM");
    }

    private static AppUser adminUser() {
        return AppUser.builder().role(AppUser.Role.ADMIN).active(true).build();
    }

    private static AppUser viewerUser() {
        return AppUser.builder().role(AppUser.Role.VIEWER).active(true).build();
    }
}
