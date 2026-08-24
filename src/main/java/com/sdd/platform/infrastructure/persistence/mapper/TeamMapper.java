package com.sdd.platform.infrastructure.persistence.mapper;

import com.sdd.platform.domain.model.Team;
import com.sdd.platform.domain.model.TeamMember;
import com.sdd.platform.domain.model.TeamMemberOption;
import com.sdd.platform.domain.model.TeamRoleOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface TeamMapper {

    Optional<Team> findById(@Param("teamId") UUID teamId);

    List<Team> findPage(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long count(@Param("keyword") String keyword, @Param("status") String status);

    boolean existsActiveCode(
            @Param("teamCode") String teamCode,
            @Param("excludeTeamId") UUID excludeTeamId
    );

    void insert(Team team);

    int update(Team team);

    int softDelete(
            @Param("teamId") UUID teamId,
            @Param("version") long version,
            @Param("deletedBy") String deletedBy,
            @Param("deletedAt") OffsetDateTime deletedAt,
            @Param("updatedBy") String updatedBy,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    List<TeamMember> findMembers(
            @Param("teamId") UUID teamId,
            @Param("status") String status
    );

    Optional<TeamMember> findMemberById(@Param("teamMemberId") UUID teamMemberId);

    boolean existsActiveMembership(
            @Param("teamId") UUID teamId,
            @Param("memberKey") UUID memberKey,
            @Param("excludeTeamMemberId") UUID excludeTeamMemberId
    );

    void insertMember(TeamMember teamMember);

    int updateMemberRole(TeamMember teamMember);

    int removeMember(
            @Param("teamMemberId") UUID teamMemberId,
            @Param("version") long version,
            @Param("deletedBy") String deletedBy,
            @Param("deletedAt") OffsetDateTime deletedAt,
            @Param("updatedBy") String updatedBy,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    boolean existsActiveMember(@Param("memberKey") UUID memberKey);

    boolean existsRole(@Param("roleId") UUID roleId);

    List<TeamMemberOption> findActiveMemberOptions(@Param("keyword") String keyword);

    List<TeamRoleOption> findRoleOptions();
}
