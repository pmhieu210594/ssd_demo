package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.domain.model.AiQualityModel;
import com.sdd.platform.web.validation.NoXssFields;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AiQualityDtos {
    private AiQualityDtos() {}

    public record AiQualityDto(
            UUID ticketAiQualityId,
            UUID projectId,
            UUID repositoryId,
            UUID ticketId,
            String ticketExternalKey,
            String ticketTitle,
            BigDecimal aiQualityRate,
            String status,
            boolean deleteFlag,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy,
            OffsetDateTime deletedAt,
            String deletedBy
    ) {
        public static AiQualityDto from(AiQualityModel model) {
            return new AiQualityDto(
                    model.getTicketAiQualityId(),
                    model.getProjectId(),
                    model.getRepositoryId(),
                    model.getTicketId(),
                    model.getTicketExternalKey(),
                    model.getTicketTitle(),
                    model.getAiQualityRate(),
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

    public record AiQualityPageDto(
            List<AiQualityDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static AiQualityPageDto from(PageResult<AiQualityModel> result) {
            return new AiQualityPageDto(
                    result.items().stream().map(AiQualityDto::from).toList(),
                    result.page(),
                    result.size(),
                    result.totalElements(),
                    result.totalPages()
            );
        }
    }

    @NoXssFields
    public record CreateAiQualityRequest(
            UUID projectId,
            UUID repositoryId,
            UUID ticketId,
            BigDecimal aiQualityRate
    ) {}

    @NoXssFields
    public record UpdateAiQualityRequest(
            BigDecimal aiQualityRate
    ) {}
}
