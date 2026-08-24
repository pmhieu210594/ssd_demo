package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseMode;
import com.sdd.platform.application.usecase.docparse.TestArtifactPairViewService;
import com.sdd.platform.web.dto.TestDocParseDtos.TestArtifactPairViewDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/demo/test-artifact-pair-views")
public class TestArtifactPairViewController {

    private final TestArtifactPairViewService service;

    public TestArtifactPairViewController(TestArtifactPairViewService service) {
        this.service = service;
    }

    @GetMapping("/tickets/{ticketId}")
    public TestArtifactPairViewDto getPairView(
            @PathVariable UUID ticketId,
            @RequestParam(defaultValue = "DRAFT") ParseMode parseMode) {
        return TestArtifactPairViewDto.from(service.getPairView(ticketId, parseMode));
    }
}
