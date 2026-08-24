package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.pmdashboard.PmDashboardService;
import com.sdd.platform.domain.model.AuthUserContext;
import com.sdd.platform.web.dto.PmDashboardDtos;
import com.sdd.platform.web.security.CurrentUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pm/dashboard")
public class PmDashboardController {

    private final PmDashboardService service;

    public PmDashboardController(PmDashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public PmDashboardDtos.PmDashboardSummaryDto summary(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String periodKey,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) String phaseCode,
            @RequestParam(required = false) String scoreBand,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String search,
            @CurrentUser AuthUserContext caller
    ) {
        return PmDashboardDtos.PmDashboardSummaryDto.from(
                service.summary(projectId, periodKey, repositoryId, phaseCode, scoreBand, riskLevel, search, caller)
        );
    }

    @GetMapping("/insights")
    public PmDashboardDtos.PmDashboardInsightsDto insights(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String periodKey,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) String phaseCode,
            @RequestParam(required = false) String scoreBand,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String search,
            @CurrentUser AuthUserContext caller
    ) {
        return PmDashboardDtos.PmDashboardInsightsDto.from(
                service.insights(projectId, periodKey, repositoryId, phaseCode, scoreBand, riskLevel, search, caller)
        );
    }

    @GetMapping("/tickets")
    public PmDashboardDtos.PmDashboardPageDto tickets(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String periodKey,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) String phaseCode,
            @RequestParam(required = false) String scoreBand,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser AuthUserContext caller
    ) {
        return PmDashboardDtos.PmDashboardPageDto.from(
                service.tickets(projectId, periodKey, repositoryId, phaseCode, scoreBand, riskLevel, search, page, size, caller)
        );
    }

    @GetMapping("/tickets/{ticketId}/detail")
    public PmDashboardDtos.PmDashboardTicketDetailDto detail(
            @PathVariable UUID ticketId,
            @CurrentUser AuthUserContext caller
    ) {
        return PmDashboardDtos.PmDashboardTicketDetailDto.from(service.detail(ticketId, caller));
    }

    @PostMapping("/refresh")
    public PmDashboardDtos.PmDashboardRefreshDto refresh(@CurrentUser AuthUserContext caller) {
        return PmDashboardDtos.PmDashboardRefreshDto.from(service.refresh(caller));
    }

    @GetMapping("/access")
    public ResponseEntity<Void> access(
            @RequestParam(required = false) UUID projectId,
            @CurrentUser AuthUserContext caller
    ) {
        if (projectId != null) {
            service.requirePm(caller, projectId);
        } else {
            service.requireAnyAccess(caller);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/options")
    public PmDashboardDtos.PmDashboardOptionsDto options(
            @RequestParam(required = false) UUID projectId,
            @CurrentUser AuthUserContext caller
    ) {
        return PmDashboardDtos.PmDashboardOptionsDto.from(service.options(projectId, caller));
    }

    @GetMapping("/template-usage")
    public List<PmDashboardDtos.TemplateUsageDto> templateUsage(
            @RequestParam UUID projectId,
            @RequestParam UUID repositoryId,
            @CurrentUser AuthUserContext caller
    ) {
        return service.getTemplateUsage(caller, projectId, repositoryId).stream()
                .map(PmDashboardDtos.TemplateUsageDto::from)
                .toList();
    }

    @GetMapping("/ai-finding-stats")
    public PmDashboardDtos.AiFindingStatsDto aiFindingStats(
            @RequestParam UUID projectId,
            @RequestParam UUID repositoryId,
            @CurrentUser AuthUserContext caller
    ) {
        return PmDashboardDtos.AiFindingStatsDto.from(service.getAiFindingStats(caller, projectId, repositoryId));
    }

    @PostMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String periodKey,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) String phaseCode,
            @RequestParam(required = false) String scoreBand,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String search,
            @CurrentUser AuthUserContext caller
    ) {
        byte[] csv = service.exportCsv(projectId, periodKey, repositoryId, phaseCode, scoreBand, riskLevel, search, caller);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=pm-dashboard.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }
}
