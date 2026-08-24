package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.qadashboard.QaDashboardService;
import com.sdd.platform.web.dto.QaDashboardDtos;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/qa/dashboard")
public class QaDashboardController {

    private final QaDashboardService service;

    public QaDashboardController(QaDashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public QaDashboardDtos.QaDashboardSummaryDto summary(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) UUID ticketId,
            @RequestParam(required = false) String search,
            @CurrentUser AuthUserContext caller
    ) {
        service.requireQaAccess(caller, projectId);
        return QaDashboardDtos.QaDashboardSummaryDto.from(
                service.getSummary(projectId, repositoryId, ticketId, search)
        );
    }

    @GetMapping("/acceptance-criteria")
    public QaDashboardDtos.AcceptanceCriteriaPageDto acceptanceCriteria(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) UUID ticketId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser AuthUserContext caller
    ) {
        service.requireQaAccess(caller, projectId);
        return QaDashboardDtos.AcceptanceCriteriaPageDto.from(
                service.getAcceptanceCriteria(projectId, repositoryId, ticketId, search, page, size)
        );
    }

    @GetMapping("/coverage-trend")
    public List<QaDashboardDtos.AcCoverageTrendPointDto> coverageTrend(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) UUID ticketId,
            @RequestParam(required = false) String search,
            @CurrentUser AuthUserContext caller
    ) {
        service.requireQaAccess(caller, projectId);
        return service.getCoverageTrend(projectId, repositoryId, ticketId, search)
                .stream()
                .map(QaDashboardDtos.AcCoverageTrendPointDto::from)
                .toList();
    }

    @GetMapping("/access")
    public ResponseEntity<Void> access(
            @RequestParam(required = false) UUID projectId,
            @CurrentUser AuthUserContext caller
    ) {
        if (projectId != null) {
            service.requireQaAccess(caller, projectId);
        } else {
            service.requireAnyAccess(caller);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/options")
    public QaDashboardDtos.QaDashboardOptionsDto options(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @CurrentUser AuthUserContext caller
    ) {
        return QaDashboardDtos.QaDashboardOptionsDto.from(service.getOptions(projectId, repositoryId, caller));
    }

    @GetMapping("/tickets")
    public QaDashboardDtos.QaTicketPageDto tickets(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) UUID ticketId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser AuthUserContext caller
    ) {
        service.requireQaAccess(caller, projectId);
        return QaDashboardDtos.QaTicketPageDto.from(
                service.getTickets(projectId, repositoryId, ticketId, search, sortBy, sortDir, page, size)
        );
    }

    @GetMapping("/tickets/{ticketId}/detail")
    public QaDashboardDtos.QaTicketDetailDto ticketDetail(
            @PathVariable UUID ticketId,
            @CurrentUser AuthUserContext caller
    ) {
        return QaDashboardDtos.QaTicketDetailDto.from(service.getTicketDetail(ticketId, caller));
    }

    @GetMapping("/tickets/{ticketId}/acceptance-criteria")
    public QaDashboardDtos.QaAcTicketPageDto ticketAcceptanceCriteria(
            @PathVariable UUID ticketId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser AuthUserContext caller
    ) {
        return QaDashboardDtos.QaAcTicketPageDto.from(
                service.getTicketAcceptanceCriteria(ticketId, search, page, size, caller)
        );
    }

    @GetMapping("/tickets/{ticketId}/coverage-trend")
    public List<QaDashboardDtos.AcCoverageTrendPointDto> ticketCoverageTrend(
            @PathVariable UUID ticketId,
            @CurrentUser AuthUserContext caller
    ) {
        return service.getTicketCoverageTrend(ticketId, caller)
                .stream()
                .map(QaDashboardDtos.AcCoverageTrendPointDto::from)
                .toList();
    }

    @PostMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) UUID ticketId,
            @RequestParam(required = false) String search,
            @CurrentUser AuthUserContext caller
    ) {
        service.requireQaAccess(caller, projectId);
        byte[] csv = service.exportCsv(projectId, repositoryId, ticketId, search);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=qa-dashboard.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }
}
