package com.sdd.platform.infrastructure.persistence.mapper;

import com.sdd.platform.domain.model.RoleOption;
import com.sdd.platform.domain.model.UserAccountAdminView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface UserAccountAdminMapper {

    List<UserAccountAdminView> findPage(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("roleIds") List<UUID> roleIds,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long count(@Param("keyword") String keyword, @Param("status") String status, @Param("roleIds") List<UUID> roleIds);

    Optional<UserAccountAdminView> findById(@Param("accountId") UUID accountId);

    Optional<RoleOption> findRoleById(@Param("roleId") UUID roleId);

    List<RoleOption> findRoles();

    boolean existsUsername(@Param("username") String username, @Param("excludeAccountId") UUID excludeAccountId);

    long countActiveAdminsExcluding(@Param("accountId") UUID accountId);

    void insertMember(
            @Param("memberKey") UUID memberKey,
            @Param("roleId") UUID roleId,
            @Param("pseudonym") String pseudonym,
            @Param("actor") String actor,
            @Param("now") OffsetDateTime now
    );

    void insertAccount(
            @Param("accountId") UUID accountId,
            @Param("memberKey") UUID memberKey,
            @Param("username") String username,
            @Param("fullname") String fullname,
            @Param("email") String email,
            @Param("passwordHash") String passwordHash,
            @Param("passwordAlgo") String passwordAlgo,
            @Param("active") boolean active,
            @Param("actor") String actor,
            @Param("now") OffsetDateTime now
    );

    int updateAccount(
            @Param("accountId") UUID accountId,
            @Param("fullname") String fullname,
            @Param("email") String email,
            @Param("active") boolean active,
            @Param("actor") String actor,
            @Param("now") OffsetDateTime now
    );

    int updateMemberRole(
            @Param("memberKey") UUID memberKey,
            @Param("roleId") UUID roleId,
            @Param("actor") String actor,
            @Param("now") OffsetDateTime now
    );

    int updateActive(
            @Param("accountId") UUID accountId,
            @Param("active") boolean active,
            @Param("actor") String actor,
            @Param("now") OffsetDateTime now
    );

    int updatePassword(
            @Param("accountId") UUID accountId,
            @Param("passwordHash") String passwordHash,
            @Param("passwordAlgo") String passwordAlgo,
            @Param("actor") String actor,
            @Param("now") OffsetDateTime now
    );
}
