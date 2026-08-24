package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.AuthUserAccountRepositoryPort;
import com.sdd.platform.domain.model.AuthUserAccount;
import com.sdd.platform.infrastructure.persistence.mapper.AuthUserAccountMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
public class AuthUserAccountRepositoryAdapter implements AuthUserAccountRepositoryPort {

    private final AuthUserAccountMapper mapper;

    public AuthUserAccountRepositoryAdapter(AuthUserAccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AuthUserAccount> findByUsername(String username) {
        Optional<AuthUserAccount> account = mapper.findByUsername(username);
        account.ifPresent(value -> {
            if (value.getRoleName() == null || value.getRoleName().isBlank()) {
                value.setRoleName("VIEWER");
            }
            if (value.getAccessScopes() == null || value.getAccessScopes().isEmpty()) {
                value.setAccessScopes(
                        mapper.findAccessScopesByMemberKey(value.getMemberKey()).stream()
                                .map(scope -> scope == null ? "" : scope)
                                .filter(scope -> !scope.isBlank())
                                .toList()
                );
            }
            value.setPasswordAlgo(value.getPasswordAlgo() == null
                    ? "bcrypt"
                    : value.getPasswordAlgo().toLowerCase(Locale.ROOT));
        });
        return account;
    }
}
