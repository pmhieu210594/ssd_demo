package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.dataopsdashboard.DataOpsDashboardService;
import com.sdd.platform.domain.model.AuthUserContext;
import com.sdd.platform.web.dto.DataOpsDashboardDtos;
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
@RequestMapping("/api/v1/data-ops/dashboard")
public class DataOpsDashboardController {

    private final DataOpsDashboardService service;

    public DataOpsDashboardController(DataOpsDashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public DataOpsDashboardDtos.DataOpsDashboardSummaryDto summary(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) String connectorName,
            @RequestParam(required = false) String parserStatus,
            @RequestParam(required = false) String search,
            @CurrentUser AuthUserContext caller
    ) {
        return DataOpsDashboardDtos.DataOpsDashboardSummaryDto.from(
                service.summary(projectId, repositoryId, connectorName, parserStatus, search, caller)
        );
    }

    @GetMapping("/connectors")
    public DataOpsDashboardDtos.DataOpsDashboardPageDto connectors(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) String connectorName,
            @RequestParam(required = false) String parserStatus,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser AuthUserContext caller
    ) {
        return DataOpsDashboardDtos.DataOpsDashboardPageDto.from(
                service.connectors(projectId, repositoryId, connectorName, parserStatus, search, page, size, caller)
        );
    }

    @GetMapping("/connectors/{connectorId}/detail")
    public DataOpsDashboardDtos.DataOpsConnectorDetailDto detail(
            @PathVariable UUID connectorId,
            @CurrentUser AuthUserContext caller
    ) {
        return DataOpsDashboardDtos.DataOpsConnectorDetailDto.from(service.connectorDetail(connectorId, caller));
    }

    @GetMapping("/repositories/{repositoryId}/missing-evidence")
    public DataOpsDashboardDtos.DataOpsRepositoryMissingEvidenceDto repositoryMissingEvidence(
            @PathVariable UUID repositoryId,
            @CurrentUser AuthUserContext caller
    ) {
        return DataOpsDashboardDtos.DataOpsRepositoryMissingEvidenceDto.from(
                service.repositoryMissingEvidence(repositoryId, caller)
        );
    }

    @GetMapping("/access")
    public ResponseEntity<Void> access(
            @RequestParam(required = false) UUID projectId,
            @CurrentUser AuthUserContext caller
    ) {
        if (projectId != null) {
            service.requireDataOpsAccess(caller, projectId);
        } else {
            service.requireAnyAccess(caller);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/options")
    public DataOpsDashboardDtos.DataOpsDashboardOptionsDto options(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @CurrentUser AuthUserContext caller
    ) {
        return DataOpsDashboardDtos.DataOpsDashboardOptionsDto.from(
                service.options(projectId, repositoryId, caller)
        );
    }

    @PostMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID repositoryId,
            @RequestParam(required = false) String connectorName,
            @RequestParam(required = false) String parserStatus,
            @RequestParam(required = false) String search,
            @CurrentUser AuthUserContext caller
    ) {
        byte[] csv = service.exportCsv(projectId, repositoryId, connectorName, parserStatus, search, caller);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=data-ops-dashboard.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }
}
