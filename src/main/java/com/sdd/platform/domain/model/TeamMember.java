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
public class TeamMember {

    private UUID teamMemberId;
    private UUID teamId;
    private String teamName;
    private UUID memberKey;
    private String pseudonym;
    private String fullname;
    private UUID roleId;
    private String roleName;
    private TeamMemberStatus status;
    private OffsetDateTime joinedAt;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private OffsetDateTime deletedAt;
    private String deletedBy;
    private long version;

    public boolean isDeleted() {
        return TeamMemberStatus.INACTIVE == status || deletedAt != null;
    }

    public enum TeamMemberStatus {
        ACTIVE,
        INACTIVE
    }
}
