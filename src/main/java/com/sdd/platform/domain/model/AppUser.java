package com.sdd.platform.domain.model;

import lombok.*;

import java.time.OffsetDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AppUser {

    private Long id;
    private String provider;
    private String providerUid;
    private String email;
    private String displayName;
    private String avatarUrl;
    private Role role;
    private boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public enum Role { VIEWER, EDITOR, ADMIN }
}
