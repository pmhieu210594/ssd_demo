package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.domain.model.RoleOption;
import com.sdd.platform.domain.model.UserAccountAdminView;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountAdminRepositoryPort {

    List<UserAccountAdminView> findPage(String keyword, String status, List<UUID> roleIds, int offset, int limit);

    long count(String keyword, String status, List<UUID> roleIds);

    Optional<UserAccountAdminView> findById(UUID accountId);

    Optional<RoleOption> findRoleById(UUID roleId);

    List<RoleOption> findRoles();

    boolean existsUsername(String username, UUID excludeAccountId);

    long countActiveAdminsExcluding(UUID accountId);

    void insertMember(UUID memberKey, UUID roleId, String pseudonym, String actor, OffsetDateTime now);

    void insertAccount(
            UUID accountId,
            UUID memberKey,
            String username,
            String fullname,
            String email,
            String passwordHash,
            String passwordAlgo,
            boolean active,
            String actor,
            OffsetDateTime now
    );

    int updateAccount(UUID accountId, String fullname, String email, boolean active, String actor, OffsetDateTime now);

    int updateMemberRole(UUID memberKey, UUID roleId, String actor, OffsetDateTime now);

    int updateActive(UUID accountId, boolean active, String actor, OffsetDateTime now);

    int updatePassword(UUID accountId, String passwordHash, String passwordAlgo, String actor, OffsetDateTime now);
}
