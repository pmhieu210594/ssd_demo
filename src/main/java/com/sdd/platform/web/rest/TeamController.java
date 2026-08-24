package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.application.usecase.governance.TeamService;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.web.dto.TeamDtos;
import com.sdd.platform.web.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamService service;

    public TeamController(TeamService service) {
        this.service = service;
    }

    @GetMapping
    public TeamDtos.TeamPageDto list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser AppUser caller
    ) {
        PageResult<com.sdd.platform.domain.model.Team> result = service.search(keyword, status, page, size, caller);
        return TeamDtos.TeamPageDto.from(result);
    }

    @GetMapping("/{teamId}")
    public TeamDtos.TeamDetailDto get(@PathVariable UUID teamId, @CurrentUser AppUser caller) {
        return TeamDtos.TeamDetailDto.from(
                service.get(teamId, caller),
                service.listMembers(teamId, caller),
                service.listMemberOptions(caller),
                service.listRoleOptions(caller)
        );
    }

    @PostMapping
    public ResponseEntity<TeamDtos.TeamDto> create(
            @Valid @RequestBody TeamDtos.CreateTeamRequest request,
            @CurrentUser AppUser caller
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                TeamDtos.TeamDto.from(service.create(
                        request.teamCode(),
                        request.teamName(),
                        request.description(),
                        caller
                ))
        );
    }

    @PutMapping("/{teamId}")
    public TeamDtos.TeamDto update(
            @PathVariable UUID teamId,
            @Valid @RequestBody TeamDtos.UpdateTeamRequest request,
            @CurrentUser AppUser caller
    ) {
        return TeamDtos.TeamDto.from(service.update(
                teamId,
                request.teamCode(),
                request.teamName(),
                request.description(),
                request.status(),
                request.version(),
                caller
        ));
    }

    @PatchMapping("/{teamId}/delete")
    public TeamDtos.TeamDto softDelete(
            @PathVariable UUID teamId,
            @RequestBody TeamDtos.DeleteTeamRequest request,
            @CurrentUser AppUser caller
    ) {
        return TeamDtos.TeamDto.from(service.softDelete(teamId, request.version(), caller));
    }

    @GetMapping("/{teamId}/members")
    public java.util.List<TeamDtos.TeamMemberDto> listMembers(@PathVariable UUID teamId, @CurrentUser AppUser caller) {
        return service.listMembers(teamId, caller).stream().map(TeamDtos.TeamMemberDto::from).toList();
    }

    @PostMapping("/{teamId}/members")
    public TeamDtos.TeamMemberDto addMember(
            @PathVariable UUID teamId,
            @RequestBody TeamDtos.AddTeamMemberRequest request,
            @CurrentUser AppUser caller
    ) {
        return TeamDtos.TeamMemberDto.from(service.addMember(teamId, request.memberKey(), request.roleId(), caller));
    }

    @PutMapping("/{teamId}/members/{teamMemberId}")
    public TeamDtos.TeamMemberDto updateMemberRole(
            @PathVariable UUID teamId,
            @PathVariable UUID teamMemberId,
            @RequestBody TeamDtos.UpdateTeamMemberRequest request,
            @CurrentUser AppUser caller
    ) {
        return TeamDtos.TeamMemberDto.from(service.updateMemberRole(
                teamId,
                teamMemberId,
                request.roleId(),
                request.version(),
                caller
        ));
    }

    @PatchMapping("/{teamId}/members/{teamMemberId}/delete")
    public TeamDtos.TeamMemberDto removeMember(
            @PathVariable UUID teamId,
            @PathVariable UUID teamMemberId,
            @RequestBody TeamDtos.DeleteTeamMemberRequest request,
            @CurrentUser AppUser caller
    ) {
        return TeamDtos.TeamMemberDto.from(service.removeMember(teamId, teamMemberId, request.version(), caller));
    }
}
