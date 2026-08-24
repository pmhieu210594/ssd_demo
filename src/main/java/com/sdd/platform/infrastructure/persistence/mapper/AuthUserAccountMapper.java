package com.sdd.platform.infrastructure.persistence.mapper;

import com.sdd.platform.domain.model.AuthUserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface AuthUserAccountMapper {

    Optional<AuthUserAccount> findByUsername(@Param("username") String username);

    List<String> findAccessScopesByMemberKey(@Param("memberKey") UUID memberKey);
}
