package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.TeamRepositoryPort;
import com.sdd.platform.domain.model.Team;
import com.sdd.platform.domain.model.TeamMember;
import com.sdd.platform.domain.model.TeamMemberOption;
import com.sdd.platform.domain.model.TeamRoleOption;
import com.sdd.platform.infrastructure.persistence.mapper.TeamMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TeamRepositoryAdapter implements TeamRepositoryPort {

    private final TeamMapper mapper;

    public TeamRepositoryAdapter(TeamMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Team> findById(UUID teamId) {
        return mapper.findById(teamId);
    }

    @Override
    public List<Team> findPage(String keyword, String status, int offset, int limit) {
        return mapper.findPage(keyword, status, offset, limit);
    }

    @Override
    public long count(String keyword, String status) {
        return mapper.count(keyword, status);
    }

    @Override
    public boolean existsActiveCode(String teamCode, UUID excludeTeamId) {
        return mapper.existsActiveCode(teamCode, excludeTeamId);
    }

    @Override
    public Team insert(Team team) {
        mapper.insert(team);
        return team;
    }

    @Override
    public int update(Team team) {
        return mapper.update(team);
    }

    @Override
    public int softDelete(UUID teamId, long version, String deletedBy, OffsetDateTime deletedAt, String updatedBy, OffsetDateTime updatedAt) {
        return mapper.softDelete(teamId, version, deletedBy, deletedAt, updatedBy, updatedAt);
    }

    @Override
    public List<TeamMember> findMembers(UUID teamId, String status) {
        return mapper.findMembers(teamId, status);
    }

    @Override
    public Optional<TeamMember> findMemberById(UUID teamMemberId) {
        return mapper.findMemberById(teamMemberId);
    }

    @Override
    public boolean existsActiveMembership(UUID teamId, UUID memberKey, UUID excludeTeamMemberId) {
        return mapper.existsActiveMembership(teamId, memberKey, excludeTeamMemberId);
    }

    @Override
    public TeamMember insertMember(TeamMember teamMember) {
        mapper.insertMember(teamMember);
        return teamMember;
    }

    @Override
    public int updateMemberRole(TeamMember teamMember) {
        return mapper.updateMemberRole(teamMember);
    }

    @Override
    public int removeMember(UUID teamMemberId, long version, String deletedBy, OffsetDateTime deletedAt, String updatedBy, OffsetDateTime updatedAt) {
        return mapper.removeMember(teamMemberId, version, deletedBy, deletedAt, updatedBy, updatedAt);
    }

    @Override
    public boolean existsActiveMember(UUID memberKey) {
        return mapper.existsActiveMember(memberKey);
    }

    @Override
    public boolean existsRole(UUID roleId) {
        return mapper.existsRole(roleId);
    }

    @Override
    public List<TeamMemberOption> findActiveMemberOptions(String keyword) {
        return mapper.findActiveMemberOptions(keyword);
    }

    @Override
    public List<TeamRoleOption> findRoleOptions() {
        return mapper.findRoleOptions();
    }
}
