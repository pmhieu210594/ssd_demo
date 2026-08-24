package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardService;
import com.sdd.platform.web.dto.SecurityDashboardDtos;
import com.sdd.platform.domain.model.AuthUserContext;
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
@RequestMapping("/api/v1/security/dashboard")
public class SecurityDashboardController {

    private final SecurityDashboardService service;

    public SecurityDashboardController(SecurityDashboardService service) {
        this.service = service;
    }

    @GetMapping("/access")
    public ResponseEntity<Void> access(
            @RequestParam(required = false) UUID projectId,
            @CurrentUser AuthUserContext caller
    ) {
        if (projectId != null) {
            service.requireSecurityAccess(caller, projectId);
        } else {
            service.requireAnyAccess(caller);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/options")
    public SecurityDashboardDtos.SecurityDashboardOptionsDto options(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @CurrentUser AuthUserContext caller
    ) {
        return SecurityDashboardDtos.SecurityDashboardOptionsDto.from(
                service.getOptions(projectId, repositoryId, caller)
        );
    }

    @GetMapping("/summary")
    public SecurityDashboardDtos.SecurityDashboardSummaryDto summary(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String safetyStatus,
            @RequestParam(required = false) String secretScanStatus,
            @RequestParam(required = false) String sastStatus,
            @RequestParam(required = false) String exceptionStatus,
            @CurrentUser AuthUserContext caller
    ) {
        service.requireSecurityAccess(caller, projectId);
        return SecurityDashboardDtos.SecurityDashboardSummaryDto.from(
                service.getSummary(projectId, repositoryId, search,
                        safetyStatus, secretScanStatus, sastStatus, exceptionStatus)
        );
    }

    @GetMapping("/tickets")
    public SecurityDashboardDtos.SecurityTicketPageDto tickets(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String safetyStatus,
            @RequestParam(required = false) String secretScanStatus,
            @RequestParam(required = false) String sastStatus,
            @RequestParam(required = false) String exceptionStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser AuthUserContext caller
    ) {
        service.requireSecurityAccess(caller, projectId);
        return SecurityDashboardDtos.SecurityTicketPageDto.from(
                service.getTickets(projectId, repositoryId, search,
                        safetyStatus, secretScanStatus, sastStatus, exceptionStatus, page, size)
        );
    }

    @GetMapping("/tickets/{ticketId}")
    public SecurityDashboardDtos.SecurityTicketDetailDto ticketDetail(
            @PathVariable UUID ticketId,
            @CurrentUser AuthUserContext caller
    ) {
        UUID projectId = service.getProjectIdForTicket(ticketId);
        service.requireSecurityAccess(caller, projectId);
        return SecurityDashboardDtos.SecurityTicketDetailDto.from(service.getTicketDetail(ticketId));
    }

    @PostMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String safetyStatus,
            @RequestParam(required = false) String secretScanStatus,
            @RequestParam(required = false) String sastStatus,
            @RequestParam(required = false) String exceptionStatus,
            @CurrentUser AuthUserContext caller
    ) {
        service.requireSecurityAccess(caller, projectId);
        byte[] csv = service.exportCsv(projectId, repositoryId, search,
                safetyStatus, secretScanStatus, sastStatus, exceptionStatus);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=security-dashboard.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }
}
