package com.sdd.platform.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Team {

    private UUID teamId;
    private String teamCode;
    private String teamName;
    private String description;
    private TeamStatus status;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private OffsetDateTime deletedAt;
    private String deletedBy;
    private long version;
    private long memberCount;

    public boolean isDeleted() {
        return TeamStatus.DELETED == status || deletedAt != null;
    }

    public enum TeamStatus {
        ACTIVE,
        DELETED
    }
}
