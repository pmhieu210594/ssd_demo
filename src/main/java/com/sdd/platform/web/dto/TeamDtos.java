package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.domain.model.Team;
import com.sdd.platform.domain.model.TeamMember;
import com.sdd.platform.domain.model.TeamMemberOption;
import com.sdd.platform.domain.model.TeamRoleOption;
import com.sdd.platform.web.validation.NoXssFields;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class TeamDtos {
    private TeamDtos() {}

    public record TeamDto(
            UUID teamId,
            String teamCode,
            String teamName,
            String description,
            String status,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy,
            OffsetDateTime deletedAt,
            String deletedBy,
            long version,
            long memberCount
    ) {
        public static TeamDto from(Team team) {
            return new TeamDto(
                    team.getTeamId(),
                    team.getTeamCode(),
                    team.getTeamName(),
                    team.getDescription(),
                    team.getStatus() == null ? null : team.getStatus().name(),
                    team.getCreatedAt(),
                    team.getCreatedBy(),
                    team.getUpdatedAt(),
                    team.getUpdatedBy(),
                    team.getDeletedAt(),
                    team.getDeletedBy(),
                    team.getVersion(),
                    team.getMemberCount()
            );
        }
    }

    public record TeamPageDto(
            List<TeamDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static TeamPageDto from(PageResult<Team> result) {
            return new TeamPageDto(
                    result.items().stream().map(TeamDto::from).toList(),
                    result.page(),
                    result.size(),
                    result.totalElements(),
                    result.totalPages()
            );
        }
    }

    public record TeamMemberDto(
            UUID teamMemberId,
            UUID teamId,
            String teamName,
            UUID memberKey,
            String pseudonym,
            UUID roleId,
            String roleName,
            String status,
            OffsetDateTime joinedAt,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy,
            OffsetDateTime deletedAt,
            String deletedBy,
            long version
    ) {
        public static TeamMemberDto from(TeamMember teamMember) {
            return new TeamMemberDto(
                    teamMember.getTeamMemberId(),
                    teamMember.getTeamId(),
                    teamMember.getTeamName(),
                    teamMember.getMemberKey(),
                    teamMember.getPseudonym(),
                    teamMember.getRoleId(),
                    teamMember.getRoleName(),
                    teamMember.getStatus() == null ? null : teamMember.getStatus().name(),
                    teamMember.getJoinedAt(),
                    teamMember.getCreatedAt(),
                    teamMember.getCreatedBy(),
                    teamMember.getUpdatedAt(),
                    teamMember.getUpdatedBy(),
                    teamMember.getDeletedAt(),
                    teamMember.getDeletedBy(),
                    teamMember.getVersion()
            );
        }
    }

    public record TeamMemberOptionDto(
            UUID memberKey,
            String pseudonym,
            String fullname
    ) {
        public static TeamMemberOptionDto from(TeamMemberOption option) {
            return new TeamMemberOptionDto(option.getMemberKey(), option.getPseudonym(), option.getFullname());
        }
    }

    public record TeamRoleOptionDto(
            UUID roleId,
            String roleName
    ) {
        public static TeamRoleOptionDto from(TeamRoleOption option) {
            return new TeamRoleOptionDto(option.getRoleId(), option.getRoleName());
        }
    }

    public record TeamDetailDto(
            TeamDto team,
            List<TeamMemberDto> members,
            List<TeamMemberOptionDto> memberOptions,
            List<TeamRoleOptionDto> roleOptions
    ) {
        public static TeamDetailDto from(
                Team team,
                List<TeamMember> members,
                List<TeamMemberOption> memberOptions,
                List<TeamRoleOption> roleOptions
        ) {
            return new TeamDetailDto(
                    TeamDto.from(team),
                    members.stream().map(TeamMemberDto::from).toList(),
                    memberOptions.stream().map(TeamMemberOptionDto::from).toList(),
                    roleOptions.stream().map(TeamRoleOptionDto::from).toList()
            );
        }
    }

    @NoXssFields
    public record CreateTeamRequest(
            String teamCode,
            String teamName,
            String description
    ) {
    }

    @NoXssFields
    public record UpdateTeamRequest(
            String teamCode,
            String teamName,
            String description,
            String status,
            long version
    ) {
    }

    public record DeleteTeamRequest(long version) {
    }

    public record AddTeamMemberRequest(
            UUID memberKey,
            UUID roleId
    ) {
    }

    public record UpdateTeamMemberRequest(
            UUID roleId,
            long version
    ) {
    }

    public record DeleteTeamMemberRequest(long version) {
    }
}
