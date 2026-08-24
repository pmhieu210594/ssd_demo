package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.domain.model.AuthUserAccount;

import java.util.Optional;

public interface AuthUserAccountRepositoryPort {

    Optional<AuthUserAccount> findByUsername(String username);
}
