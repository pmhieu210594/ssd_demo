package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.governance.OrganizationService;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.web.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test-support/organizations")
public class OrganizationTestSupportController {

    private static final String DEFAULT_TEST_PREFIX = "E2E_ORG_";

    private final OrganizationService service;

    public OrganizationTestSupportController(OrganizationService service) {
        this.service = service;
    }

    @DeleteMapping
    public ResponseEntity<Void> purgeByPrefix(
            @RequestParam(defaultValue = DEFAULT_TEST_PREFIX) String prefix,
            @CurrentUser AppUser caller
    ) {
        service.purgeTestDataByCodePrefix(prefix, caller);
        return ResponseEntity.noContent().build();
    }
}
