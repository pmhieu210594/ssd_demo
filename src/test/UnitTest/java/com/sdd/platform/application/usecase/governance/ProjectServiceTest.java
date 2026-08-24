package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.ProjectRepositoryPort;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.Project;
import com.sdd.platform.domain.model.ProjectTeamAssignment;
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
class ProjectServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROJECT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TEAM_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID TEAM_ID_2 = UUID.fromString("30000000-0000-0000-0000-000000000002");

    @Mock
    private ProjectRepositoryPort repository;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    private ProjectService service;

    @BeforeEach
    void setUp() {
        service = new ProjectService(repository, adminAuditLogService);
    }

    @Test
    void search_requiresAdminAndDefaultsToActiveFilter() {
        when(repository.findPage("Data", CUSTOMER_ID, "ACTIVE", 20, 20)).thenReturn(List.of(activeProject()));
        when(repository.count("Data", CUSTOMER_ID, "ACTIVE")).thenReturn(21L);

        PageResult<Project> result = service.search(CUSTOMER_ID, "  Data  ", null, 1, 0, adminUser());

        assertThat(result.items()).hasSize(1);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @Test
    void nonAdminIsDeniedForEveryEntryPoint() {
        AppUser viewer = AppUser.builder().role(AppUser.Role.VIEWER).active(true).build();

        assertThatThrownBy(() -> service.search(null, null, null, 0, 20, viewer))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Pages.Project.Error.Forbidden");
        assertThatThrownBy(() -> service.get(PROJECT_ID, viewer))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Pages.Project.Error.Forbidden");
        assertThatThrownBy(() -> service.create(CUSTOMER_ID, "Project", "Internal", "LOW", List.of(), viewer))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Pages.Project.Error.Forbidden");
        assertThatThrownBy(() -> service.update(PROJECT_ID, CUSTOMER_ID, "Project", "Internal", "LOW", List.of(), viewer))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Pages.Project.Error.Forbidden");
        assertThatThrownBy(() -> service.softDelete(PROJECT_ID, viewer))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Pages.Project.Error.Forbidden");
    }

    @Test
    void get_returnsActiveProjectWithAssignments() {
        when(repository.findById(PROJECT_ID)).thenReturn(Optional.of(activeProject()));
        when(repository.findActiveTeamAssignments(PROJECT_ID)).thenReturn(List.of(activeAssignment(TEAM_ID)));

        Project result = service.get(PROJECT_ID, adminUser());

        assertThat(result.getTeamAssignments()).hasSize(1);
        assertThat(result.getTeamAssignments().get(0).getTeamId()).isEqualTo(TEAM_ID);
    }

    @Test
    void create_trimsValidatesAndSyncsTeams() {
        when(repository.existsActiveCustomer(CUSTOMER_ID)).thenReturn(true);
        when(repository.existsActiveAlias(CUSTOMER_ID, "Project A", null)).thenReturn(false);
        when(repository.findActiveTeamIdsByIds(List.of(TEAM_ID, TEAM_ID_2))).thenReturn(List.of(TEAM_ID, TEAM_ID_2));
        when(repository.findActiveTeamAssignments(PROJECT_ID)).thenReturn(List.of(), List.of(activeAssignment(TEAM_ID), activeAssignment(TEAM_ID_2)));
        when(repository.findById(PROJECT_ID)).thenReturn(Optional.of(activeProject()));

        when(repository.insert(any())).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setProjectId(PROJECT_ID);
            return project;
        });

        Project created = service.create(CUSTOMER_ID, "  Project A  ", "  Internal  ", " low ", List.of(TEAM_ID, TEAM_ID, TEAM_ID_2), adminUser());

        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        verify(repository).insert(projectCaptor.capture());
        assertThat(projectCaptor.getValue().getProjectAlias()).isEqualTo("Project A");
        assertThat(projectCaptor.getValue().getProjectType()).isEqualTo("Internal");
        assertThat(projectCaptor.getValue().getRiskLevel()).isEqualTo(Project.RiskLevel.LOW);
        verify(repository, org.mockito.Mockito.times(2)).insertTeamAssignment(any(ProjectTeamAssignment.class));
        assertThat(created.getProjectAlias()).isEqualTo("Project Alpha");
    }

    @Test
    void create_normalizesBlankProjectTypeToNullAndRejectsTooLongValue() {
        when(repository.existsActiveCustomer(CUSTOMER_ID)).thenReturn(true);
        when(repository.existsActiveAlias(CUSTOMER_ID, "Project A", null)).thenReturn(false);
        when(repository.findById(PROJECT_ID)).thenReturn(Optional.of(activeProject()));
        when(repository.findActiveTeamAssignments(PROJECT_ID)).thenReturn(List.of(), List.of());
        when(repository.insert(any())).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setProjectId(PROJECT_ID);
            return project;
        });

        service.create(CUSTOMER_ID, "Project A", "   ", "LOW", List.of(), adminUser());

        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        verify(repository).insert(projectCaptor.capture());
        assertThat(projectCaptor.getValue().getProjectType()).isNull();

        String tooLongProjectType = "x".repeat(101);
        assertThatThrownBy(() -> service.create(CUSTOMER_ID, "Project B", tooLongProjectType, "LOW", List.of(), adminUser()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.Project.ProjectType.MaxLength");
    }

    @Test
    void create_rejectsMissingCustomerDuplicateAliasAndInvalidTeam() {
        assertThatThrownBy(() -> service.create(null, "Project", "Internal", "LOW", List.of(), adminUser()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.Project.Customer.Required");

        when(repository.existsActiveCustomer(CUSTOMER_ID)).thenReturn(true);
        when(repository.existsActiveAlias(CUSTOMER_ID, "Project", null)).thenReturn(true);
        assertThatThrownBy(() -> service.create(CUSTOMER_ID, "Project", "Internal", "LOW", List.of(), adminUser()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.Project.Alias.Duplicate");

        when(repository.existsActiveAlias(CUSTOMER_ID, "Project", null)).thenReturn(false);
        when(repository.findActiveTeamIdsByIds(List.of(TEAM_ID))).thenReturn(List.of());
        assertThatThrownBy(() -> service.create(CUSTOMER_ID, "Project", "Internal", "LOW", List.of(TEAM_ID), adminUser()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pages.Project.Team.Invalid");
        verify(repository, never()).insert(any(Project.class));
    }

    @Test
    void update_reconcilesTeamAssignmentsWithoutVersion() {
        Project existing = activeProject();
        when(repository.findById(PROJECT_ID)).thenReturn(Optional.of(existing), Optional.of(existing));
        when(repository.existsActiveCustomer(CUSTOMER_ID)).thenReturn(true);
        when(repository.existsActiveAlias(CUSTOMER_ID, "Project B", PROJECT_ID)).thenReturn(false);
        when(repository.findActiveTeamIdsByIds(List.of(TEAM_ID_2))).thenReturn(List.of(TEAM_ID_2));
        when(repository.update(any())).thenReturn(1);
        // update() now reads team assignments 3x: once for the pre-mutation audit
        // snapshot, once for reconciliation (must match the "before" state so the
        // diff still fires), and once for the final post-sync reload.
        when(repository.findActiveTeamAssignments(PROJECT_ID)).thenReturn(
                List.of(activeAssignment(TEAM_ID)), List.of(activeAssignment(TEAM_ID)), List.of(activeAssignment(TEAM_ID_2)));

        service.update(PROJECT_ID, CUSTOMER_ID, " Project B ", "  Customer Facing  ", " info ", List.of(TEAM_ID_2), adminUser());

        verify(repository).deactivateTeamAssignment(eq(PROJECT_ID), eq(TEAM_ID), eq("admin@example.com"), any(), eq("admin@example.com"), any());
        verify(repository).insertTeamAssignment(any(ProjectTeamAssignment.class));
    }

    @Test
    void softDelete_marksProjectDeleted() {
        Project deleted = activeProject();
        deleted.setStatus(Project.ProjectStatus.DELETED);
        deleted.setDeleteFlag(true);
        deleted.setDeletedAt(OffsetDateTime.now());
        when(repository.findById(PROJECT_ID)).thenReturn(Optional.of(activeProject()), Optional.of(deleted));
        when(repository.softDelete(eq(PROJECT_ID), eq("admin@example.com"), any(), eq("admin@example.com"), any())).thenReturn(1);

        Project result = service.softDelete(PROJECT_ID, adminUser());

        assertThat(result.isDeleted()).isTrue();
    }

    @Test
    void getAndDeleteRejectDeletedProjectAsUnavailable() {
        Project deleted = activeProject();
        deleted.setStatus(Project.ProjectStatus.DELETED);
        deleted.setDeleteFlag(true);
        deleted.setDeletedAt(OffsetDateTime.now());
        when(repository.findById(PROJECT_ID)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.get(PROJECT_ID, adminUser()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Pages.Project.NotFound");
        assertThatThrownBy(() -> service.softDelete(PROJECT_ID, adminUser()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Pages.Project.NotFound");
    }

    private AppUser adminUser() {
        return AppUser.builder()
                .email("admin@example.com")
                .displayName("Admin")
                .role(AppUser.Role.ADMIN)
                .active(true)
                .build();
    }

    private Project activeProject() {
        return Project.builder()
                .projectId(PROJECT_ID)
                .customerId(CUSTOMER_ID)
                .customerName("Customer Demo")
                .projectAlias("Project Alpha")
                .projectType("Internal")
                .riskLevel(Project.RiskLevel.MEDIUM)
                .status(Project.ProjectStatus.ACTIVE)
                .deleteFlag(false)
                .createdAt(OffsetDateTime.parse("2026-06-16T00:00:00Z"))
                .createdBy("admin@example.com")
                .updatedAt(OffsetDateTime.parse("2026-06-16T00:00:00Z"))
                .updatedBy("admin@example.com")
                .build();
    }

    private ProjectTeamAssignment activeAssignment(UUID teamId) {
        return ProjectTeamAssignment.builder()
                .projectTeamId(UUID.randomUUID())
                .projectId(PROJECT_ID)
                .teamId(teamId)
                .teamCode("TEAM-" + teamId.toString().substring(0, 4))
                .teamName("Team " + teamId.toString().substring(0, 4))
                .status(Project.ProjectStatus.ACTIVE)
                .createdAt(OffsetDateTime.parse("2026-06-16T00:00:00Z"))
                .createdBy("admin@example.com")
                .updatedAt(OffsetDateTime.parse("2026-06-16T00:00:00Z"))
                .updatedBy("admin@example.com")
                .build();
    }
}
