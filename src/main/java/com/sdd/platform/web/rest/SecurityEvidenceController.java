package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.governance.SafetyPackService;
import com.sdd.platform.application.port.out.persistence.SecurityScanRepositoryPort;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.web.dto.SafetyPackDtos;
import com.sdd.platform.web.dto.SecurityScanDtos;
import com.sdd.platform.web.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class SecurityEvidenceController {

    private final SafetyPackService safetyPackService;
    private final SecurityScanRepositoryPort securityScanRepositoryPort;

    public SecurityEvidenceController(SafetyPackService safetyPackService,
                                      SecurityScanRepositoryPort securityScanRepositoryPort) {
        this.safetyPackService = safetyPackService;
        this.securityScanRepositoryPort = securityScanRepositoryPort;
    }

    @GetMapping("/safety-packs")
    public java.util.List<SafetyPackDtos.SafetyPackStatusDto> listSafetyPacks(
            @RequestParam(defaultValue = "30") int limit,
            @CurrentUser AppUser caller
    ) {
        return safetyPackService.recent(limit, caller).stream()
                .map(SafetyPackDtos.SafetyPackStatusDto::from)
                .toList();
    }

    @GetMapping("/security-scans")
    public java.util.List<SecurityScanDtos.SecurityScanDto> listSecurityScans(
            @RequestParam(defaultValue = "30") int limit,
            @CurrentUser AppUser caller
    ) {
        return securityScanRepositoryPort.findRecent(limit).stream()
                .map(SecurityScanDtos.SecurityScanDto::from)
                .toList();
    }
}
