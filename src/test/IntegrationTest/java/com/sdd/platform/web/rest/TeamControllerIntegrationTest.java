package com.sdd.platform.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.exception.OptimisticLockingException;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.application.usecase.governance.TeamService;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.Team;
import com.sdd.platform.domain.model.TeamMember;
import com.sdd.platform.domain.model.TeamMemberOption;
import com.sdd.platform.domain.model.TeamRoleOption;
import com.sdd.platform.web.exception.GlobalExceptionHandler;
import com.sdd.platform.web.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamControllerIntegrationTest {

        private static final UUID TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000001001");
        private static final UUID TEAM_MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000004001");
        private static final UUID MEMBER_KEY = UUID.fromString("00000000-0000-0000-0000-000000002001");
        private static final UUID ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000003001");
        private static final UUID OTHER_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000003002");

        private MockMvc mockMvc;
        private ObjectMapper objectMapper;
        private TeamService service;
        private AppUser caller;

        @BeforeEach
        void setUp() {
                service = mock(TeamService.class);
                objectMapper = new ObjectMapper().findAndRegisterModules();
                caller = AppUser.builder()
                                .id(1L)
                                .provider("internal")
                                .providerUid("team-admin")
                                .email("team-admin@example.com")
                                .displayName("Team Admin")
                                .role(AppUser.Role.ADMIN)
                                .active(true)
                                .build();

                mockMvc = MockMvcBuilders
                                .standaloneSetup(new TeamController(service))
                                .setControllerAdvice(new GlobalExceptionHandler())
                                .setCustomArgumentResolvers(new Phase6CurrentUserArgumentResolver(caller))
                                .build();
        }

        @Test
        void list_returnsPagedTeamsAndPassesSearchParameters() throws Exception {
                Team team = team(TEAM_ID, "IT_TEAM_001", "Integration Team", "Desc", Team.TeamStatus.ACTIVE, 3L, 2L);
                when(service.search(eq("Integration"), eq("ACTIVE"), eq(1), eq(10), eq(caller)))
                                .thenReturn(new PageResult<>(List.of(team), 1, 10, 1, 1));

                mockMvc.perform(get("/api/v1/teams")
                                .param("keyword", "Integration")
                                .param("status", "ACTIVE")
                                .param("page", "1")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items[0].teamCode").value("IT_TEAM_001"))
                                .andExpect(jsonPath("$.items[0].teamName").value("Integration Team"))
                                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                                .andExpect(jsonPath("$.items[0].memberCount").value(2))
                                .andExpect(jsonPath("$.page").value(1))
                                .andExpect(jsonPath("$.size").value(10))
                                .andExpect(jsonPath("$.totalElements").value(1))
                                .andExpect(jsonPath("$.totalPages").value(1));

                verify(service).search("Integration", "ACTIVE", 1, 10, caller);
        }

        @Test
        void get_returnsDetailWithMembersAndLookupOptions() throws Exception {
                Team team = team(TEAM_ID, "IT_TEAM_001", "Integration Team", "Desc", Team.TeamStatus.ACTIVE, 3L, 1L);
                TeamMember member = teamMember(TEAM_MEMBER_ID, TEAM_ID, MEMBER_KEY, ROLE_ID, "Developer",
                                TeamMember.TeamMemberStatus.ACTIVE, 7L);
                when(service.get(eq(TEAM_ID), eq(caller))).thenReturn(team);
                when(service.listMembers(eq(TEAM_ID), eq(caller))).thenReturn(List.of(member));
                when(service.listMemberOptions(eq(caller))).thenReturn(List.of(
                                TeamMemberOption.builder().memberKey(MEMBER_KEY).pseudonym("dev-001")
                                                .fullname("Dev One").build()));
                when(service.listRoleOptions(eq(caller))).thenReturn(List.of(
                                TeamRoleOption.builder().roleId(ROLE_ID).roleName("Developer").build()));

                mockMvc.perform(get("/api/v1/teams/{teamId}", TEAM_ID))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.team.teamCode").value("IT_TEAM_001"))
                                .andExpect(jsonPath("$.members[0].pseudonym").value("dev-001"))
                                .andExpect(jsonPath("$.members[0].roleName").value("Developer"))
                                .andExpect(jsonPath("$.memberOptions[0].memberKey").value(MEMBER_KEY.toString()))
                                .andExpect(jsonPath("$.memberOptions[0].fullname").value("Dev One"))
                                .andExpect(jsonPath("$.roleOptions[0].roleName").value("Developer"));
        }

        @Test
        void create_returns201CreatedTeam() throws Exception {
                when(service.create(eq("IT_TEAM_002"), eq("Integration Team Two"), eq("Desc"), eq(caller)))
                                .thenReturn(team(TEAM_ID, "IT_TEAM_002", "Integration Team Two", "Desc",
                                                Team.TeamStatus.ACTIVE, 0L, 0L));

                mockMvc.perform(post("/api/v1/teams")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "teamCode", "IT_TEAM_002",
                                                "teamName", "Integration Team Two",
                                                "description", "Desc"))))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.teamCode").value("IT_TEAM_002"))
                                .andExpect(jsonPath("$.teamName").value("Integration Team Two"))
                                .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        void update_allowsTeamCodeChangeAndReturnsUpdatedTeam() throws Exception {
                when(service.update(eq(TEAM_ID), eq("IT_TEAM_003"), eq("Integration Team Updated"), eq("Updated"),
                                eq("ACTIVE"), eq(3L), eq(caller)))
                                .thenReturn(team(TEAM_ID, "IT_TEAM_003", "Integration Team Updated", "Updated",
                                                Team.TeamStatus.ACTIVE, 4L, 2L));

                mockMvc.perform(put("/api/v1/teams/{teamId}", TEAM_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "teamCode", "IT_TEAM_003",
                                                "teamName", "Integration Team Updated",
                                                "description", "Updated",
                                                "status", "ACTIVE",
                                                "version", 3L))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.teamCode").value("IT_TEAM_003"))
                                .andExpect(jsonPath("$.teamName").value("Integration Team Updated"))
                                .andExpect(jsonPath("$.version").value(4));
        }

        @Test
        void softDelete_returnsDeletedTeam() throws Exception {
                when(service.softDelete(eq(TEAM_ID), eq(4L), eq(caller)))
                                .thenReturn(team(TEAM_ID, "IT_TEAM_001", "Integration Team", "Desc",
                                                Team.TeamStatus.DELETED, 5L, 0L));

                mockMvc.perform(patch("/api/v1/teams/{teamId}/delete", TEAM_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("version", 4L))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("DELETED"))
                                .andExpect(jsonPath("$.deletedAt").exists());
        }

        @Test
        void listMembers_returnsOnlyMemberDtos() throws Exception {
                when(service.listMembers(eq(TEAM_ID), eq(caller))).thenReturn(List.of(
                                teamMember(TEAM_MEMBER_ID, TEAM_ID, MEMBER_KEY, ROLE_ID, "Developer",
                                                TeamMember.TeamMemberStatus.ACTIVE, 7L)));

                mockMvc.perform(get("/api/v1/teams/{teamId}/members", TEAM_ID))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].teamMemberId").value(TEAM_MEMBER_ID.toString()))
                                .andExpect(jsonPath("$[0].memberKey").value(MEMBER_KEY.toString()))
                                .andExpect(jsonPath("$[0].roleName").value("Developer"))
                                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
        }

        @Test
        void addMember_returnsCreatedMembershipBody() throws Exception {
                when(service.addMember(eq(TEAM_ID), eq(MEMBER_KEY), eq(ROLE_ID), eq(caller)))
                                .thenReturn(teamMember(TEAM_MEMBER_ID, TEAM_ID, MEMBER_KEY, ROLE_ID, "Developer",
                                                TeamMember.TeamMemberStatus.ACTIVE, 0L));

                mockMvc.perform(post("/api/v1/teams/{teamId}/members", TEAM_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "memberKey", MEMBER_KEY,
                                                "roleId", ROLE_ID))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.teamId").value(TEAM_ID.toString()))
                                .andExpect(jsonPath("$.memberKey").value(MEMBER_KEY.toString()))
                                .andExpect(jsonPath("$.roleId").value(ROLE_ID.toString()))
                                .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        void updateMemberRole_returnsUpdatedMembershipBody() throws Exception {
                when(service.updateMemberRole(eq(TEAM_ID), eq(TEAM_MEMBER_ID), eq(OTHER_ROLE_ID), eq(7L), eq(caller)))
                                .thenReturn(teamMember(TEAM_MEMBER_ID, TEAM_ID, MEMBER_KEY, OTHER_ROLE_ID, "Tester",
                                                TeamMember.TeamMemberStatus.ACTIVE, 8L));

                mockMvc.perform(put("/api/v1/teams/{teamId}/members/{teamMemberId}", TEAM_ID, TEAM_MEMBER_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "roleId", OTHER_ROLE_ID,
                                                "version", 7L))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.teamMemberId").value(TEAM_MEMBER_ID.toString()))
                                .andExpect(jsonPath("$.roleId").value(OTHER_ROLE_ID.toString()))
                                .andExpect(jsonPath("$.roleName").value("Tester"))
                                .andExpect(jsonPath("$.version").value(8));
        }

        @Test
        void removeMember_returnsInactiveMembershipBody() throws Exception {
                when(service.removeMember(eq(TEAM_ID), eq(TEAM_MEMBER_ID), eq(7L), eq(caller)))
                                .thenReturn(teamMember(TEAM_MEMBER_ID, TEAM_ID, MEMBER_KEY, ROLE_ID, "Developer",
                                                TeamMember.TeamMemberStatus.INACTIVE, 8L));

                mockMvc.perform(patch("/api/v1/teams/{teamId}/members/{teamMemberId}/delete", TEAM_ID, TEAM_MEMBER_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("version", 7L))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("INACTIVE"))
                                .andExpect(jsonPath("$.deletedAt").exists());
        }

        @Test
        void serviceBusinessErrorsAreMappedToHttpStatusCodes() throws Exception {
                when(service.search(nullable(String.class), nullable(String.class), eq(0), eq(20), eq(caller)))
                                .thenThrow(new ForbiddenException("Pages.Team.Error.Forbidden"));
                mockMvc.perform(get("/api/v1/teams"))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                                .andExpect(jsonPath("$.message").value("Pages.Team.Error.Forbidden"));

                when(service.create(eq("IT_TEAM_DUP"), eq("Duplicate"), eq("Desc"), eq(caller)))
                                .thenThrow(new BusinessRuleException("Pages.Team.Code.Duplicate"));
                mockMvc.perform(post("/api/v1/teams")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "teamCode", "IT_TEAM_DUP",
                                                "teamName", "Duplicate",
                                                "description", "Desc"))))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value("Pages.Team.Code.Duplicate"));

                when(service.get(eq(TEAM_ID), eq(caller))).thenThrow(new NotFoundException("Pages.Team.NotFound"));
                mockMvc.perform(get("/api/v1/teams/{teamId}", TEAM_ID))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message").value("Pages.Team.NotFound"));

                when(service.update(eq(TEAM_ID), eq("IT_TEAM_003"), eq("Integration Team Updated"), eq("Updated"),
                                eq("ACTIVE"), eq(3L), eq(caller)))
                                .thenThrow(new OptimisticLockingException("Pages.Team.Conflict.Version"));
                mockMvc.perform(put("/api/v1/teams/{teamId}", TEAM_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "teamCode", "IT_TEAM_003",
                                                "teamName", "Integration Team Updated",
                                                "description", "Updated",
                                                "status", "ACTIVE",
                                                "version", 3L))))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.message").value("Pages.Team.Conflict.Version"));
        }

        @Test
        void addMemberDuplicateErrorIsReturnedAs400() throws Exception {
                when(service.addMember(eq(TEAM_ID), eq(MEMBER_KEY), eq(ROLE_ID), eq(caller)))
                                .thenThrow(new BusinessRuleException("Pages.Team.Member.Duplicate"));

                mockMvc.perform(post("/api/v1/teams/{teamId}/members", TEAM_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "memberKey", MEMBER_KEY,
                                                "roleId", ROLE_ID))))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value("Pages.Team.Member.Duplicate"));
        }

        private Team team(UUID teamId, String code, String name, String description, Team.TeamStatus status,
                        long version, long memberCount) {
                OffsetDateTime now = OffsetDateTime.parse("2026-06-15T00:00:00Z");
                return Team.builder()
                                .teamId(teamId)
                                .teamCode(code)
                                .teamName(name)
                                .description(description)
                                .status(status)
                                .createdAt(now)
                                .createdBy("tester")
                                .updatedAt(now)
                                .updatedBy("tester")
                                .deletedAt(status == Team.TeamStatus.DELETED ? now : null)
                                .deletedBy(status == Team.TeamStatus.DELETED ? "tester" : null)
                                .version(version)
                                .memberCount(memberCount)
                                .build();
        }

        private TeamMember teamMember(
                        UUID teamMemberId,
                        UUID teamId,
                        UUID memberKey,
                        UUID roleId,
                        String roleName,
                        TeamMember.TeamMemberStatus status,
                        long version) {
                OffsetDateTime now = OffsetDateTime.parse("2026-06-15T00:00:00Z");
                return TeamMember.builder()
                                .teamMemberId(teamMemberId)
                                .teamId(teamId)
                                .teamName("Integration Team")
                                .memberKey(memberKey)
                                .pseudonym("dev-001")
                                .fullname("Dev One")
                                .roleId(roleId)
                                .roleName(roleName)
                                .status(status)
                                .joinedAt(now)
                                .createdAt(now)
                                .createdBy("tester")
                                .updatedAt(now)
                                .updatedBy("tester")
                                .deletedAt(status == TeamMember.TeamMemberStatus.INACTIVE ? now : null)
                                .deletedBy(status == TeamMember.TeamMemberStatus.INACTIVE ? "tester" : null)
                                .version(version)
                                .build();
        }

        private static class Phase6CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {
                private final AppUser caller;

                private Phase6CurrentUserArgumentResolver(AppUser caller) {
                        this.caller = caller;
                }

                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType().equals(AppUser.class)
                                        && parameter.hasParameterAnnotation(CurrentUser.class);
                }

                @Override
                public Object resolveArgument(
                                MethodParameter parameter,
                                ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest,
                                WebDataBinderFactory binderFactory) {
                        return caller;
                }
        }
}
