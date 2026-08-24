package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.domain.model.Team;
import com.sdd.platform.domain.model.TeamMember;
import com.sdd.platform.domain.model.TeamMemberOption;
import com.sdd.platform.domain.model.TeamRoleOption;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepositoryPort {

    Optional<Team> findById(UUID teamId);

    List<Team> findPage(String keyword, String status, int offset, int limit);

    long count(String keyword, String status);

    boolean existsActiveCode(String teamCode, UUID excludeTeamId);

    Team insert(Team team);

    int update(Team team);

    int softDelete(
            UUID teamId,
            long version,
            String deletedBy,
            OffsetDateTime deletedAt,
            String updatedBy,
            OffsetDateTime updatedAt
    );

    List<TeamMember> findMembers(UUID teamId, String status);

    Optional<TeamMember> findMemberById(UUID teamMemberId);

    boolean existsActiveMembership(UUID teamId, UUID memberKey, UUID excludeTeamMemberId);

    TeamMember insertMember(TeamMember teamMember);

    int updateMemberRole(TeamMember teamMember);

    int removeMember(
            UUID teamMemberId,
            long version,
            String deletedBy,
            OffsetDateTime deletedAt,
            String updatedBy,
            OffsetDateTime updatedAt
    );

    boolean existsActiveMember(UUID memberKey);

    boolean existsRole(UUID roleId);

    List<TeamMemberOption> findActiveMemberOptions(String keyword);

    List<TeamRoleOption> findRoleOptions();
}
