package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.application.usecase.ticketbugmetrics.TicketBugMetricsModels.TicketBugMetricsOption;
import com.sdd.platform.application.usecase.ticketbugmetrics.TicketBugMetricsModels.TicketBugMetricsOptions;
import com.sdd.platform.domain.model.TicketBugMetricsModel;
import com.sdd.platform.domain.model.TicketOption;
import com.sdd.platform.web.validation.NoXssFields;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class TicketBugMetricsDtos {
    private TicketBugMetricsDtos() {}

    public record TicketBugMetricsDto(
            UUID ticketBugId,
            UUID projectId,
            UUID repositoryId,
            UUID ticketId,
            String ticketExternalKey,
            String ticketTitle,
            int internalBugCount,
            int customerBugCount,
            String note,
            String status,
            boolean deleteFlag,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy,
            OffsetDateTime deletedAt,
            String deletedBy
    ) {
        public static TicketBugMetricsDto from(TicketBugMetricsModel model) {
            return new TicketBugMetricsDto(
                    model.getTicketBugId(),
                    model.getProjectId(),
                    model.getRepositoryId(),
                    model.getTicketId(),
                    model.getTicketExternalKey(),
                    model.getTicketTitle(),
                    model.getInternalBugCount(),
                    model.getCustomerBugCount(),
                    model.getNote(),
                    model.getStatus() == null ? null : model.getStatus().name(),
                    model.isDeleteFlag(),
                    model.getCreatedAt(),
                    model.getCreatedBy(),
                    model.getUpdatedAt(),
                    model.getUpdatedBy(),
                    model.getDeletedAt(),
                    model.getDeletedBy()
            );
        }
    }

    public record TicketBugMetricsPageDto(
            List<TicketBugMetricsDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static TicketBugMetricsPageDto from(PageResult<TicketBugMetricsModel> result) {
            return new TicketBugMetricsPageDto(
                    result.items().stream().map(TicketBugMetricsDto::from).toList(),
                    result.page(),
                    result.size(),
                    result.totalElements(),
                    result.totalPages()
            );
        }
    }

    public record TicketOptionDto(UUID ticketId, String externalTicketKey, String title) {
        public static TicketOptionDto from(TicketOption option) {
            return new TicketOptionDto(option.ticketId(), option.externalTicketKey(), option.title());
        }
    }

    public record TicketBugMetricsOptionDto(
            String value,
            String label,
            String role
    ) {
        public static TicketBugMetricsOptionDto from(TicketBugMetricsOption option) {
            return new TicketBugMetricsOptionDto(option.value(), option.label(), option.role());
        }
    }

    public record TicketBugMetricsOptionsDto(
            List<TicketBugMetricsOptionDto> projects,
            List<TicketBugMetricsOptionDto> repositories,
            List<TicketBugMetricsOptionDto> tickets
    ) {
        public static TicketBugMetricsOptionsDto from(TicketBugMetricsOptions options) {
            return new TicketBugMetricsOptionsDto(
                    options.projects().stream().map(TicketBugMetricsOptionDto::from).toList(),
                    options.repositories().stream().map(TicketBugMetricsOptionDto::from).toList(),
                    options.tickets().stream().map(TicketBugMetricsOptionDto::from).toList()
            );
        }
    }

    @NoXssFields
    public record CreateTicketBugMetricsRequest(
            UUID projectId,
            UUID repositoryId,
            UUID ticketId,
            Integer internalBugCount,
            Integer customerBugCount,
            String note
    ) {}

    @NoXssFields
    public record UpdateTicketBugMetricsRequest(
            Integer internalBugCount,
            Integer customerBugCount,
            String note
    ) {}
}
