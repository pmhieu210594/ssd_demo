package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.devdashboard.DevDashboardService;
import com.sdd.platform.domain.model.AuthUserContext;
import com.sdd.platform.web.dto.DevDashboardDtos;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dev/dashboard")
public class DevDashboardController {

    private final DevDashboardService service;

    public DevDashboardController(DevDashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public DevDashboardDtos.DevDashboardSummaryDto summary(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) String ciStatus,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) String parserStatus,
            @RequestParam(required = false) String search,
            @CurrentUser AuthUserContext caller
    ) {
        return DevDashboardDtos.DevDashboardSummaryDto.from(
                service.summary(projectId, repositoryId, ciStatus, reviewStatus, parserStatus, search, caller)
        );
    }

    @GetMapping("/tickets")
    public DevDashboardDtos.DevDashboardPageDto tickets(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) String ciStatus,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) String parserStatus,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser AuthUserContext caller
    ) {
        return DevDashboardDtos.DevDashboardPageDto.from(
                service.tickets(projectId, repositoryId, ciStatus, reviewStatus, parserStatus, search, page, size, caller)
        );
    }

    @GetMapping("/tickets/{ticketId}/detail")
    public DevDashboardDtos.DevTicketDetailDto detail(
            @PathVariable UUID ticketId,
            @CurrentUser AuthUserContext caller
    ) {
        return DevDashboardDtos.DevTicketDetailDto.from(service.detail(ticketId, caller));
    }

    @GetMapping("/access")
    public ResponseEntity<Void> access(
            @RequestParam(required = false) UUID projectId,
            @CurrentUser AuthUserContext caller
    ) {
        if (projectId != null) {
            service.requireDevAccess(caller, projectId);
        } else {
            service.requireAnyAccess(caller);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/options")
    public DevDashboardDtos.DevDashboardOptionsDto options(
            @RequestParam(required = false) UUID projectId,
            @CurrentUser AuthUserContext caller
    ) {
        return DevDashboardDtos.DevDashboardOptionsDto.from(service.options(projectId, caller));
    }

    @PostMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) String ciStatus,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) String parserStatus,
            @RequestParam(required = false) String search,
            @CurrentUser AuthUserContext caller
    ) {
        byte[] csv = service.exportCsv(projectId, repositoryId, ciStatus, reviewStatus, parserStatus, search, caller);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=dev-dashboard.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }
}
