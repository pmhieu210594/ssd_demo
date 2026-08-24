package com.sdd.platform.web.rest;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerService;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.web.dto.ArtifactScannerDtos.ArtifactScanRequestDto;
import com.sdd.platform.web.dto.ArtifactScannerDtos.ArtifactScanResultDto;
import com.sdd.platform.web.dto.ArtifactScannerDtos.ArtifactScanRunDto;
import com.sdd.platform.web.dto.ArtifactScannerDtos.ArtifactScanArtifactDto;
import com.sdd.platform.web.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/data-ops/artifact-scans")
public class ArtifactScannerController {

    private final ArtifactScannerService service;

    public ArtifactScannerController(ArtifactScannerService service) {
        this.service = service;
    }

    @PostMapping
    public ArtifactScanResultDto run(@Valid @RequestBody ArtifactScanRequestDto request,
                                     @CurrentUser AppUser caller) {
        assertAdmin(caller);
        var run = service.scan(request.toRequest());
        return new ArtifactScanResultDto(
                ArtifactScanRunDto.from(run),
                service.getRunArtifacts(run.connectorRunId()).stream().map(ArtifactScanArtifactDto::from).toList()
        );
    }

    @GetMapping("/{runId}")
    public ArtifactScanRunDto getRun(@PathVariable UUID runId,
                                     @CurrentUser AppUser caller) {
        assertAdmin(caller);
        return ArtifactScanRunDto.from(service.getRun(runId));
    }

    @GetMapping("/{runId}/artifacts")
    public java.util.List<ArtifactScanArtifactDto> getRunArtifacts(@PathVariable UUID runId,
                                                                   @CurrentUser AppUser caller) {
        assertAdmin(caller);
        return service.getRunArtifacts(runId).stream().map(ArtifactScanArtifactDto::from).toList();
    }

    @GetMapping("/current")
    public java.util.List<ArtifactScanArtifactDto> getCurrentInventory(@RequestParam UUID repositoryId,
                                                                       @CurrentUser AppUser caller) {
        assertAdmin(caller);
        return service.getCurrentInventory(repositoryId).stream().map(ArtifactScanArtifactDto::from).toList();
    }

    private void assertAdmin(AppUser caller) {
        if (caller == null || caller.getRole() != AppUser.Role.ADMIN) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }
}
