package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.governance.CiRunMetadataService;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.web.dto.CiRunMetadataDtos;
import com.sdd.platform.web.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ci-run-metadata")
public class CiRunMetadataController {

    private final CiRunMetadataService service;

    public CiRunMetadataController(CiRunMetadataService service) {
        this.service = service;
    }

    @GetMapping
    public java.util.List<CiRunMetadataDtos.CiRunMetadataDto> list(
            @RequestParam(defaultValue = "30") int limit,
            @CurrentUser AppUser caller
    ) {
        return service.recent(limit, caller).stream()
                .map(CiRunMetadataDtos.CiRunMetadataDto::from)
                .toList();
    }
}
