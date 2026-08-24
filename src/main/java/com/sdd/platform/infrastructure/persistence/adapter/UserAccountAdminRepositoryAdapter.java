package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.UserAccountAdminRepositoryPort;
import com.sdd.platform.domain.model.RoleOption;
import com.sdd.platform.domain.model.UserAccountAdminView;
import com.sdd.platform.infrastructure.persistence.mapper.UserAccountAdminMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserAccountAdminRepositoryAdapter implements UserAccountAdminRepositoryPort {

    private final UserAccountAdminMapper mapper;

    public UserAccountAdminRepositoryAdapter(UserAccountAdminMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<UserAccountAdminView> findPage(String keyword, String status, List<UUID> roleIds, int offset, int limit) {
        return mapper.findPage(keyword, status, roleIds, offset, limit);
    }

    @Override
    public long count(String keyword, String status, List<UUID> roleIds) {
        return mapper.count(keyword, status, roleIds);
    }

    @Override
    public Optional<UserAccountAdminView> findById(UUID accountId) {
        return mapper.findById(accountId);
    }

    @Override
    public Optional<RoleOption> findRoleById(UUID roleId) {
        return mapper.findRoleById(roleId);
    }

    @Override
    public List<RoleOption> findRoles() {
        return mapper.findRoles();
    }

    @Override
    public boolean existsUsername(String username, UUID excludeAccountId) {
        return mapper.existsUsername(username, excludeAccountId);
    }

    @Override
    public long countActiveAdminsExcluding(UUID accountId) {
        return mapper.countActiveAdminsExcluding(accountId);
    }

    @Override
    public void insertMember(UUID memberKey, UUID roleId, String pseudonym, String actor, OffsetDateTime now) {
        mapper.insertMember(memberKey, roleId, pseudonym, actor, now);
    }

    @Override
    public void insertAccount(UUID accountId, UUID memberKey, String username, String fullname, String email,
                              String passwordHash, String passwordAlgo, boolean active, String actor,
                              OffsetDateTime now) {
        mapper.insertAccount(accountId, memberKey, username, fullname, email, passwordHash, passwordAlgo, active,
                actor, now);
    }

    @Override
    public int updateAccount(UUID accountId, String fullname, String email, boolean active, String actor,
                             OffsetDateTime now) {
        return mapper.updateAccount(accountId, fullname, email, active, actor, now);
    }

    @Override
    public int updateMemberRole(UUID memberKey, UUID roleId, String actor, OffsetDateTime now) {
        return mapper.updateMemberRole(memberKey, roleId, actor, now);
    }

    @Override
    public int updateActive(UUID accountId, boolean active, String actor, OffsetDateTime now) {
        return mapper.updateActive(accountId, active, actor, now);
    }

    @Override
    public int updatePassword(UUID accountId, String passwordHash, String passwordAlgo, String actor,
                              OffsetDateTime now) {
        return mapper.updatePassword(accountId, passwordHash, passwordAlgo, actor, now);
    }
}
