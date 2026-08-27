package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.exception.OptimisticLockingException;
import com.sdd.platform.application.port.out.persistence.TeamRepositoryPort;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.Team;
import com.sdd.platform.domain.model.TeamMember;
import com.sdd.platform.domain.model.TeamMemberOption;
import com.sdd.platform.domain.model.TeamRoleOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

        private static final UUID TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000001001");
        private static final UUID OTHER_TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000001002");
        private static final UUID MEMBER_KEY = UUID.fromString("00000000-0000-0000-0000-000000002001");
        private static final UUID ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000003001");
        private static final UUID OTHER_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000003002");
        private static final UUID TEAM_MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000004001");

        @Mock
        private TeamRepositoryPort repository;

        private TeamService service;

        @BeforeEach
        void setUp() {
                service = new TeamService(repository);
        }

        @Test
        void search_requiresAdminAndNormalizesFilterAndPagination() {
                Team team = activeTeam(TEAM_ID, "UT_TEAM_001", "Unit Team");
                when(repository.findPage("Unit", "ACTIVE", 100, 100)).thenReturn(List.of(team));
                when(repository.count("Unit", "ACTIVE")).thenReturn(1L);

                PageResult<Team> result = service.search("  Unit  ", null, 1, 500, adminUser());

                assertThat(result.items()).containsExactly(team);
                assertThat(result.page()).isEqualTo(1);
                assertThat(result.size()).isEqualTo(100);
                assertThat(result.totalElements()).isEqualTo(1L);
                verify(repository).findPage("Unit", "ACTIVE", 100, 100);
                verify(repository).count("Unit", "ACTIVE");
        }

        @Test
        void nonAdminIsDeniedForTeamAndMemberEntryPoints() {
                AppUser viewer = AppUser.builder().role(AppUser.Role.VIEWER).active(true).build();

                assertThatThrownBy(() -> service.search(null, null, 0, 20, viewer))
                                .isInstanceOf(ForbiddenException.class)
                                .hasMessage("Pages.Team.Error.Forbidden");
                assertThatThrownBy(() -> service.get(TEAM_ID, viewer))
                                .isInstanceOf(ForbiddenException.class)
                                .hasMessage("Pages.Team.Error.Forbidden");
                assertThatThrownBy(() -> service.create("UT_TEAM", "Team", "Desc", viewer))
                                .isInstanceOf(ForbiddenException.class)
                                .hasMessage("Pages.Team.Error.Forbidden");
                assertThatThrownBy(() -> service.update(TEAM_ID, "UT_TEAM", "Team", "Desc", "ACTIVE", 1L, viewer))
                                .isInstanceOf(ForbiddenException.class)
                                .hasMessage("Pages.Team.Error.Forbidden");
                assertThatThrownBy(() -> service.softDelete(TEAM_ID, 1L, viewer))
                                .isInstanceOf(ForbiddenException.class)
                                .hasMessage("Pages.Team.Error.Forbidden");
                assertThatThrownBy(() -> service.listMembers(TEAM_ID, viewer))
                                .isInstanceOf(ForbiddenException.class)
                                .hasMessage("Pages.Team.Error.Forbidden");
                assertThatThrownBy(() -> service.addMember(TEAM_ID, MEMBER_KEY, ROLE_ID, viewer))
                                .isInstanceOf(ForbiddenException.class)
                                .hasMessage("Pages.Team.Error.Forbidden");
                assertThatThrownBy(() -> service.updateMemberRole(TEAM_ID, TEAM_MEMBER_ID, ROLE_ID, 1L, viewer))
                                .isInstanceOf(ForbiddenException.class)
                                .hasMessage("Pages.Team.Error.Forbidden");
                assertThatThrownBy(() -> service.removeMember(TEAM_ID, TEAM_MEMBER_ID, 1L, viewer))
                                .isInstanceOf(ForbiddenException.class)
                                .hasMessage("Pages.Team.Error.Forbidden");
        }

        @Test
        void create_trimsValuesAndPersistsActiveTeam() {
                when(repository.existsActiveCode("UT_TEAM_001", null)).thenReturn(false);
                when(repository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

                Team created = service.create("  UT_TEAM_001  ", "  Unit Team  ", "  Description  ", adminUser());

                ArgumentCaptor<Team> captor = ArgumentCaptor.forClass(Team.class);
                verify(repository).insert(captor.capture());
                Team saved = captor.getValue();
                assertThat(saved.getTeamId()).isNotNull();
                assertThat(saved.getTeamCode()).isEqualTo("UT_TEAM_001");
                assertThat(saved.getTeamName()).isEqualTo("Unit Team");
                assertThat(saved.getDescription()).isEqualTo("Description");
                assertThat(saved.getStatus()).isEqualTo(Team.TeamStatus.ACTIVE);
                assertThat(saved.getVersion()).isZero();
                assertThat(saved.getMemberCount()).isZero();
                assertThat(created).isSameAs(saved);
        }

        @Test
        void create_rejectsRequiredFieldsAndDuplicateCode() {
                assertThatThrownBy(() -> service.create(" ", "Unit Team", "Desc", adminUser()))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Team.Code.Required");
                assertThatThrownBy(() -> service.create("UT_TEAM_001", " ", "Desc", adminUser()))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Team.Name.Required");

                when(repository.existsActiveCode("UT_TEAM_DUP", null)).thenReturn(true);
                assertThatThrownBy(() -> service.create("UT_TEAM_DUP", "Duplicate", "Desc", adminUser()))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Team.Code.Duplicate");
                verify(repository, never()).insert(any());
        }

        @Test
        void update_allowsCodeChangeAndReloadsUpdatedTeam() {
                when(repository.existsActiveCode("UT_TEAM_002", TEAM_ID)).thenReturn(false);
                when(repository.update(any())).thenReturn(1);
                when(repository.findById(TEAM_ID)).thenReturn(
                                Optional.of(activeTeam(TEAM_ID, "UT_TEAM_001", "Unit Team")),
                                Optional.of(activeTeam(TEAM_ID, "UT_TEAM_002", "Unit Team Updated")));

                Team updated = service.update(TEAM_ID, " UT_TEAM_002 ", " Unit Team Updated ", " Updated desc ",
                                "ACTIVE", 3L, adminUser());

                assertThat(updated.getTeamCode()).isEqualTo("UT_TEAM_002");
                ArgumentCaptor<Team> captor = ArgumentCaptor.forClass(Team.class);
                verify(repository).update(captor.capture());
                assertThat(captor.getValue().getTeamCode()).isEqualTo("UT_TEAM_002");
                assertThat(captor.getValue().getTeamName()).isEqualTo("Unit Team Updated");
                assertThat(captor.getValue().getDescription()).isEqualTo("Updated desc");
                assertThat(captor.getValue().getVersion()).isEqualTo(3L);
        }

        @Test
        void update_rejectsDuplicateCodeDeletedTeamAndOptimisticConflict() {
                when(repository.findById(TEAM_ID))
                                .thenReturn(Optional.of(activeTeam(TEAM_ID, "UT_TEAM_001", "Unit Team")));
                when(repository.existsActiveCode("UT_TEAM_DUP", TEAM_ID)).thenReturn(true);
                assertThatThrownBy(() -> service.update(TEAM_ID, "UT_TEAM_DUP", "Unit Team", "Desc", "ACTIVE", 3L,
                                adminUser()))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Team.Code.Duplicate");

                Team deletedTeam = activeTeam(OTHER_TEAM_ID, "UT_TEAM_DEL", "Deleted Team");
                deletedTeam.setStatus(Team.TeamStatus.DELETED);
                deletedTeam.setDeletedAt(OffsetDateTime.now());
                when(repository.findById(OTHER_TEAM_ID)).thenReturn(Optional.of(deletedTeam));
                assertThatThrownBy(() -> service.update(OTHER_TEAM_ID, "UT_TEAM_DEL", "Deleted Team", "Desc", "ACTIVE",
                                3L, adminUser()))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Team.Deleted.CannotEdit");

                UUID conflictTeamId = UUID.fromString("00000000-0000-0000-0000-000000001003");
                when(repository.findById(conflictTeamId)).thenReturn(
                                Optional.of(activeTeam(conflictTeamId, "UT_TEAM_CONFLICT", "Conflict Team")));
                when(repository.existsActiveCode("UT_TEAM_CONFLICT", conflictTeamId)).thenReturn(false);
                when(repository.update(any())).thenReturn(0);
                assertThatThrownBy(() -> service.update(conflictTeamId, "UT_TEAM_CONFLICT", "Conflict Team", "Desc",
                                "ACTIVE", 99L, adminUser()))
                                .isInstanceOf(OptimisticLockingException.class)
                                .hasMessage("Pages.Team.Conflict.Version");
        }

        @Test
        void softDelete_marksTeamDeletedThroughRepositoryAndReturnsReloadedTeam() {
                Team deleted = activeTeam(TEAM_ID, "UT_TEAM_001", "Unit Team");
                deleted.setStatus(Team.TeamStatus.DELETED);
                deleted.setDeletedAt(OffsetDateTime.now());
                deleted.setDeletedBy("admin@example.com");
                when(repository.findById(TEAM_ID)).thenReturn(
                                Optional.of(activeTeam(TEAM_ID, "UT_TEAM_001", "Unit Team")), Optional.of(deleted));
                when(repository.softDelete(eq(TEAM_ID), eq(3L), eq("admin@example.com"), any(), eq("admin@example.com"),
                                any())).thenReturn(1);

                Team result = service.softDelete(TEAM_ID, 3L, adminUser());

                assertThat(result.getStatus()).isEqualTo(Team.TeamStatus.DELETED);
                verify(repository).softDelete(eq(TEAM_ID), eq(3L), eq("admin@example.com"), any(),
                                eq("admin@example.com"), any());
        }

        @Test
        void softDelete_rejectsAlreadyDeletedAndStaleVersion() {
                Team deleted = activeTeam(TEAM_ID, "UT_TEAM_001", "Unit Team");
                deleted.setStatus(Team.TeamStatus.DELETED);
                deleted.setDeletedAt(OffsetDateTime.now());
                when(repository.findById(TEAM_ID)).thenReturn(Optional.of(deleted));
                assertThatThrownBy(() -> service.softDelete(TEAM_ID, 3L, adminUser()))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Team.AlreadyDeleted");

                UUID conflictTeamId = UUID.fromString("00000000-0000-0000-0000-000000001004");
                when(repository.findById(conflictTeamId)).thenReturn(
                                Optional.of(activeTeam(conflictTeamId, "UT_TEAM_CONFLICT", "Conflict Team")));
                when(repository.softDelete(eq(conflictTeamId), eq(99L), eq("admin@example.com"), any(),
                                eq("admin@example.com"), any())).thenReturn(0);
                assertThatThrownBy(() -> service.softDelete(conflictTeamId, 99L, adminUser()))
                                .isInstanceOf(OptimisticLockingException.class)
                                .hasMessage("Pages.Team.Conflict.Version");
        }

        @Test
        void listMembers_returnsActiveMembershipsOnly() {
                TeamMember member = activeMembership(TEAM_MEMBER_ID, TEAM_ID, MEMBER_KEY, ROLE_ID, "Developer");
                when(repository.findById(TEAM_ID))
                                .thenReturn(Optional.of(activeTeam(TEAM_ID, "UT_TEAM_001", "Unit Team")));
                when(repository.findMembers(TEAM_ID, "ACTIVE")).thenReturn(List.of(member));

                List<TeamMember> result = service.listMembers(TEAM_ID, adminUser());

                assertThat(result).containsExactly(member);
                verify(repository).findMembers(TEAM_ID, "ACTIVE");
        }

        @Test
        void addMember_validatesMasterDataAndPersistsActiveMembership() {
                when(repository.findById(TEAM_ID))
                                .thenReturn(Optional.of(activeTeam(TEAM_ID, "UT_TEAM_001", "Unit Team")));
                when(repository.existsActiveMember(MEMBER_KEY)).thenReturn(true);
                when(repository.existsRole(ROLE_ID)).thenReturn(true);
                when(repository.existsActiveMembership(TEAM_ID, MEMBER_KEY, null)).thenReturn(false);
                when(repository.insertMember(any())).thenAnswer(invocation -> invocation.getArgument(0));

                TeamMember created = service.addMember(TEAM_ID, MEMBER_KEY, ROLE_ID, adminUser());

                ArgumentCaptor<TeamMember> captor = ArgumentCaptor.forClass(TeamMember.class);
                verify(repository).insertMember(captor.capture());
                TeamMember saved = captor.getValue();
                assertThat(saved.getTeamId()).isEqualTo(TEAM_ID);
                assertThat(saved.getMemberKey()).isEqualTo(MEMBER_KEY);
                assertThat(saved.getRoleId()).isEqualTo(ROLE_ID);
                assertThat(saved.getStatus()).isEqualTo(TeamMember.TeamMemberStatus.ACTIVE);
                assertThat(saved.getVersion()).isZero();
                assertThat(created).isSameAs(saved);
        }

        @Test
        void addMember_rejectsMissingMemberMissingRoleInactiveTeamAndDuplicateSameTeam() {
                when(repository.findById(TEAM_ID))
                                .thenReturn(Optional.of(activeTeam(TEAM_ID, "UT_TEAM_001", "Unit Team")));
                assertThatThrownBy(() -> service.addMember(TEAM_ID, null, ROLE_ID, adminUser()))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Team.Member.Required");
                assertThatThrownBy(() -> service.addMember(TEAM_ID, MEMBER_KEY, null, adminUser()))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Team.Role.Required");

                when(repository.existsActiveMember(MEMBER_KEY)).thenReturn(false);
                assertThatThrownBy(() -> service.addMember(TEAM_ID, MEMBER_KEY, ROLE_ID, adminUser()))
                                .isInstanceOf(NotFoundException.class)
                                .hasMessage("Pages.Team.Member.NotFound");

                UUID teamWithoutRoleId = UUID.fromString("00000000-0000-0000-0000-000000001005");
                when(repository.findById(teamWithoutRoleId))
                                .thenReturn(Optional.of(activeTeam(teamWithoutRoleId, "UT_TEAM_ROLE", "Role Team")));
                when(repository.existsActiveMember(MEMBER_KEY)).thenReturn(true);
                when(repository.existsRole(ROLE_ID)).thenReturn(false);
                assertThatThrownBy(() -> service.addMember(teamWithoutRoleId, MEMBER_KEY, ROLE_ID, adminUser()))
                                .isInstanceOf(NotFoundException.class)
                                .hasMessage("Pages.Team.Role.NotFound");

                UUID duplicateTeamId = UUID.fromString("00000000-0000-0000-0000-000000001006");
                when(repository.findById(duplicateTeamId)).thenReturn(
                                Optional.of(activeTeam(duplicateTeamId, "UT_TEAM_DUP_MEMBER", "Duplicate Team")));
                when(repository.existsActiveMember(MEMBER_KEY)).thenReturn(true);
                when(repository.existsRole(ROLE_ID)).thenReturn(true);
                when(repository.existsActiveMembership(duplicateTeamId, MEMBER_KEY, null)).thenReturn(true);
                assertThatThrownBy(() -> service.addMember(duplicateTeamId, MEMBER_KEY, ROLE_ID, adminUser()))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Team.Member.Duplicate");

                Team deleted = activeTeam(OTHER_TEAM_ID, "UT_TEAM_DELETED", "Deleted Team");
                deleted.setStatus(Team.TeamStatus.DELETED);
                deleted.setDeletedAt(OffsetDateTime.now());
                when(repository.findById(OTHER_TEAM_ID)).thenReturn(Optional.of(deleted));
                assertThatThrownBy(() -> service.addMember(OTHER_TEAM_ID, MEMBER_KEY, ROLE_ID, adminUser()))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessage("Pages.Team.NotActive");
        }

        @Test
        void addMember_allowsSameMemberInDifferentTeamsWhenSameTeamDuplicateCheckIsFalse() {
                when(repository.findById(OTHER_TEAM_ID))
                                .thenReturn(Optional.of(activeTeam(OTHER_TEAM_ID, "UT_TEAM_002", "Other Team")));
                when(repository.existsActiveMember(MEMBER_KEY)).thenReturn(true);
                when(repository.existsRole(ROLE_ID)).thenReturn(true);
                when(repository.existsActiveMembership(OTHER_TEAM_ID, MEMBER_KEY, null)).thenReturn(false);
                when(repository.insertMember(any())).thenAnswer(invocation -> invocation.getArgument(0));

                TeamMember created = service.addMember(OTHER_TEAM_ID, MEMBER_KEY, ROLE_ID, adminUser());

                assertThat(created.getTeamId()).isEqualTo(OTHER_TEAM_ID);
                assertThat(created.getMemberKey()).isEqualTo(MEMBER_KEY);
        }

        @Test
        void updateMemberRole_updatesExistingActiveMembershipWithoutCreatingDuplicate() {
                TeamMember existing = activeMembership(TEAM_MEMBER_ID, TEAM_ID, MEMBER_KEY, ROLE_ID, "Developer");
                TeamMember reloaded = activeMembership(TEAM_MEMBER_ID, TEAM_ID, MEMBER_KEY, OTHER_ROLE_ID, "Tester");
                when(repository.findById(TEAM_ID))
                                .thenReturn(Optional.of(activeTeam(TEAM_ID, "UT_TEAM_001", "Unit Team")));
                when(repository.existsRole(OTHER_ROLE_ID)).thenReturn(true);
                when(repository.findMemberById(TEAM_MEMBER_ID)).thenReturn(Optional.of(existing),
                                Optional.of(reloaded));
                when(repository.updateMemberRole(any())).thenReturn(1);

                TeamMember updated = service.updateMemberRole(TEAM_ID, TEAM_MEMBER_ID, OTHER_ROLE_ID, 7L, adminUser());

                assertThat(updated.getRoleId()).isEqualTo(OTHER_ROLE_ID);
                verify(repository).updateMemberRole(existing);
                verify(repository, never()).insertMember(any());
        }

        @Test
        void updateMemberRole_rejectsMissingRoleWrongTeamInactiveMembershipAndConflict() {
                when(repository.findById(TEAM_ID))
                                .thenReturn(Optional.of(activeTeam(TEAM_ID, "UT_TEAM_001", "Unit Team")));
                when(repository.existsRole(OTHER_ROLE_ID)).thenReturn(false);
                assertThatThrownBy(
                                () -> service.updateMemberRole(TEAM_ID, TEAM_MEMBER_ID, OTHER_ROLE_ID, 7L, adminUser()))
                                .isInstanceOf(NotFoundException.class)
                                .hasMessage("Pages.Team.Role.NotFound");

                UUID wrongMemberId = UUID.fromString("00000000-0000-0000-0000-000000004002");
                when(repository.existsRole(ROLE_ID)).thenReturn(true);
                when(repository.findMemberById(wrongMemberId)).thenReturn(Optional
                                .of(activeMembership(wrongMemberId, OTHER_TEAM_ID, MEMBER_KEY, ROLE_ID, "Developer")));
                assertThatThrownBy(() -> service.updateMemberRole(TEAM_ID, wrongMemberId, ROLE_ID, 7L, adminUser()))
                                .isInstanceOf(NotFoundException.class)
                                .hasMessage("Pages.Team.Member.NotFound");

                UUID inactiveMemberId = UUID.fromString("00000000-0000-0000-0000-000000004003");
                TeamMember inactive = activeMembership(inactiveMemberId, TEAM_ID, MEMBER_KEY, ROLE_ID, "Developer");
                inactive.setStatus(TeamMember.TeamMemberStatus.INACTIVE);
                inactive.setDeletedAt(OffsetDateTime.now());
                when(repository.findMemberById(inactiveMemberId)).thenReturn(Optional.of(inactive));
                assertThatThrownBy(() -> service.updateMemberRole(TEAM_ID, inactiveMemberId, ROLE_ID, 7L, adminUser()))
                                .isInstanceOf(NotFoundException.class)
                                .hasMessage("Pages.Team.Member.NotFound");

                UUID conflictMemberId = UUID.fromString("00000000-0000-0000-0000-000000004004");
                when(repository.findMemberById(conflictMemberId)).thenReturn(Optional
                                .of(activeMembership(conflictMemberId, TEAM_ID, MEMBER_KEY, ROLE_ID, "Developer")));
                when(repository.updateMemberRole(any())).thenReturn(0);
                assertThatThrownBy(() -> service.updateMemberRole(TEAM_ID, conflictMemberId, ROLE_ID, 99L, adminUser()))
                                .isInstanceOf(OptimisticLockingException.class)
                                .hasMessage("Pages.Team.Conflict.Version");
        }

        @Test
        void removeMember_marksMembershipInactiveThroughRepository() {
                TeamMember active = activeMembership(TEAM_MEMBER_ID, TEAM_ID, MEMBER_KEY, ROLE_ID, "Developer");
                TeamMember removed = activeMembership(TEAM_MEMBER_ID, TEAM_ID, MEMBER_KEY, ROLE_ID, "Developer");
                removed.setStatus(TeamMember.TeamMemberStatus.INACTIVE);
                removed.setDeletedAt(OffsetDateTime.now());
                removed.setDeletedBy("admin@example.com");
                when(repository.findById(TEAM_ID))
                                .thenReturn(Optional.of(activeTeam(TEAM_ID, "UT_TEAM_001", "Unit Team")));
                when(repository.findMemberById(TEAM_MEMBER_ID)).thenReturn(Optional.of(active), Optional.of(removed));
                when(repository.removeMember(eq(TEAM_MEMBER_ID), eq(7L), eq("admin@example.com"), any(),
                                eq("admin@example.com"), any())).thenReturn(1);

                TeamMember result = service.removeMember(TEAM_ID, TEAM_MEMBER_ID, 7L, adminUser());

                assertThat(result.getStatus()).isEqualTo(TeamMember.TeamMemberStatus.INACTIVE);
                verify(repository).removeMember(eq(TEAM_MEMBER_ID), eq(7L), eq("admin@example.com"), any(),
                                eq("admin@example.com"), any());
        }

        @Test
        void removeMember_rejectsInactiveMembershipAndConflict() {
                TeamMember inactive = activeMembership(TEAM_MEMBER_ID, TEAM_ID, MEMBER_KEY, ROLE_ID, "Developer");
                inactive.setStatus(TeamMember.TeamMemberStatus.INACTIVE);
                inactive.setDeletedAt(OffsetDateTime.now());
                when(repository.findById(TEAM_ID))
                                .thenReturn(Optional.of(activeTeam(TEAM_ID, "UT_TEAM_001", "Unit Team")));
                when(repository.findMemberById(TEAM_MEMBER_ID)).thenReturn(Optional.of(inactive));
                assertThatThrownBy(() -> service.removeMember(TEAM_ID, TEAM_MEMBER_ID, 7L, adminUser()))
                                .isInstanceOf(NotFoundException.class)
                                .hasMessage("Pages.Team.Member.NotFound");

                UUID conflictMemberId = UUID.fromString("00000000-0000-0000-0000-000000004005");
                when(repository.findMemberById(conflictMemberId)).thenReturn(Optional
                                .of(activeMembership(conflictMemberId, TEAM_ID, MEMBER_KEY, ROLE_ID, "Developer")));
                when(repository.removeMember(eq(conflictMemberId), eq(99L), eq("admin@example.com"), any(),
                                eq("admin@example.com"), any())).thenReturn(0);
                assertThatThrownBy(() -> service.removeMember(TEAM_ID, conflictMemberId, 99L, adminUser()))
                                .isInstanceOf(OptimisticLockingException.class)
                                .hasMessage("Pages.Team.Conflict.Version");
        }

        @Test
        void listOptions_returnsMemberAndRoleMasterData() {
                when(repository.findActiveMemberOptions(null)).thenReturn(List.of(
                                TeamMemberOption.builder().memberKey(MEMBER_KEY).pseudonym("dev-001")
                                                .fullname("Dev One").build()));
                when(repository.findRoleOptions()).thenReturn(List.of(
                                TeamRoleOption.builder().roleId(ROLE_ID).roleName("Developer").build()));

                assertThat(service.listMemberOptions(adminUser())).extracting(TeamMemberOption::getPseudonym)
                                .containsExactly("dev-001");
                assertThat(service.listRoleOptions(adminUser())).extracting(TeamRoleOption::getRoleName)
                                .containsExactly("Developer");
        }

        @Test
        void get_throwsNotFoundWhenMissing() {
                when(repository.findById(TEAM_ID)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.get(TEAM_ID, adminUser()))
                                .isInstanceOf(NotFoundException.class)
                                .hasMessage("Pages.Team.NotFound");
        }

        private AppUser adminUser() {
                return AppUser.builder()
                                .id(1L)
                                .provider("internal")
                                .providerUid("team-admin")
                                .email("admin@example.com")
                                .displayName("Team Admin")
                                .role(AppUser.Role.ADMIN)
                                .active(true)
                                .build();
        }

        private Team activeTeam(UUID teamId, String code, String name) {
                OffsetDateTime now = OffsetDateTime.parse("2026-06-15T00:00:00Z");
                return Team.builder()
                                .teamId(teamId)
                                .teamCode(code)
                                .teamName(name)
                                .description("Description")
                                .status(Team.TeamStatus.ACTIVE)
                                .createdAt(now)
                                .createdBy("admin@example.com")
                                .updatedAt(now)
                                .updatedBy("admin@example.com")
                                .version(3L)
                                .memberCount(1L)
                                .build();
        }

        private TeamMember activeMembership(UUID teamMemberId, UUID teamId, UUID memberKey, UUID roleId,
                        String roleName) {
                OffsetDateTime now = OffsetDateTime.parse("2026-06-15T00:00:00Z");
                return TeamMember.builder()
                                .teamMemberId(teamMemberId)
                                .teamId(teamId)
                                .teamName("Unit Team")
                                .memberKey(memberKey)
                                .pseudonym("dev-001")
                                .fullname("Dev One")
                                .roleId(roleId)
                                .roleName(roleName)
                                .status(TeamMember.TeamMemberStatus.ACTIVE)
                                .joinedAt(now)
                                .createdAt(now)
                                .createdBy("admin@example.com")
                                .updatedAt(now)
                                .updatedBy("admin@example.com")
                                .version(7L)
                                .build();
        }
}