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
public class AuthUserContext {

    private UUID userAccountId;
    private String username;
    private String displayName;
    private String email;
    private String role;
    @Builder.Default
    private List<String> accessScopes = new ArrayList<>();
}
