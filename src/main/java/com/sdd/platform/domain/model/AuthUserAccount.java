package com.sdd.platform.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthUserAccount {

    private UUID userAccountId;
    private UUID memberKey;
    private String username;
    private String fullname;
    private String email;
    private String passwordHash;
    private String passwordAlgo;
    private boolean active;
    private String roleName;
    private Integer roleDeleteFlag;
    @Builder.Default
    private List<String> accessScopes = new ArrayList<>();

    public boolean isRoleDeleted() {
        return roleDeleteFlag != null && roleDeleteFlag == 1;
    }
}
