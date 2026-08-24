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
public class UserAccountAdminView {

    private UUID accountId;
    private UUID memberKey;
    private String username;
    private String fullname;
    private String email;
    private UUID roleId;
    private String roleName;
    private UUID teamId;
    private String teamName;
    private boolean active;
    private OffsetDateTime lastLoginAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
