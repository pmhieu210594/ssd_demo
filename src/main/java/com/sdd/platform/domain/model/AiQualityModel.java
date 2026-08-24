package com.sdd.platform.domain.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiQualityModel {

    private UUID ticketAiQualityId;
    private UUID projectId;
    private UUID repositoryId;
    private UUID ticketId;
    private String ticketExternalKey;
    private String ticketTitle;
    private BigDecimal aiQualityRate;
    private Status status;
    private boolean deleteFlag;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private OffsetDateTime deletedAt;
    private String deletedBy;

    public boolean isDeleted() {
        return deleteFlag || deletedAt != null || status == Status.DELETED;
    }

    public enum Status { ACTIVE, DELETED }
}
